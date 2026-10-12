"""
SQMS - Wait-time model training
Dataset: Hospital patient data (Abdulqader Asiri, Kaggle) - hospital_data_sampleee.xlsx
Target : minutes from Entry Time to Post-Consultation Time (queue wait + consultation)

Only features that are knowable at the moment a citizen joins the queue are used,
so the trained model can be served by ml-service and called from queue-service.

Run from ml-service/ with hospital_data_sampleee.xlsx sitting in scripts/
alongside this file:
    python scripts/train_wait_time.py

Data source: downloads via the Kaggle API (kagglehub) when KAGGLE_API_TOKEN
is set, same as the original notebook. Falls back to the local xlsx already
checked into scripts/ when no token is available, so this still runs
out-of-the-box in this repo/CI without Kaggle credentials.
"""
import glob
import os
from pathlib import Path

import json
import numpy as np
import pandas as pd
import joblib

from sklearn.model_selection import train_test_split, cross_val_score, KFold
from sklearn.preprocessing import StandardScaler, OneHotEncoder
from sklearn.compose import ColumnTransformer
from sklearn.pipeline import Pipeline
from sklearn.impute import SimpleImputer

from sklearn.neighbors import KNeighborsRegressor
from sklearn.linear_model import LinearRegression
from sklearn.tree import DecisionTreeRegressor
from sklearn.ensemble import RandomForestRegressor, GradientBoostingRegressor

from sklearn.metrics import mean_absolute_error, r2_score, mean_squared_error

SCRIPT_DIR = Path(__file__).resolve().parent
LOCAL_DATA_PATH = SCRIPT_DIR / "hospital_data_sampleee.xlsx"
KAGGLE_DATASET = "abdulqaderasiirii/hospital-patient-data"

OUTPUT_DIR = SCRIPT_DIR.parent / "app" / "artifacts"  # shipped in the Docker image, see Dockerfile
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
MODEL_OUT = OUTPUT_DIR / "wait_time_model.joblib"
METADATA_OUT = OUTPUT_DIR / "feature_metadata.json"  # sidecar so the router can report confidence/method without unpickling the pipeline
COMPARISON_OUT = OUTPUT_DIR / "model_comparison.csv"
RANDOM_STATE = 42

# -----------------------------
# AUTH: env var, or Colab Secret named KAGGLE_API_TOKEN
# (must be set BEFORE importing kagglehub)
# -----------------------------
if not os.environ.get("KAGGLE_API_TOKEN"):
    try:
        from google.colab import userdata          # Colab Secrets (key icon, left sidebar)
        os.environ["KAGGLE_API_TOKEN"] = userdata.get("KAGGLE_API_TOKEN")
    except ImportError:
        pass                                        # not running in Colab

# -----------------------------
# LOAD (via Kaggle API when a token is available; otherwise fall back to
# the copy already checked into scripts/, so this still runs without
# Kaggle credentials in this repo/CI)
# -----------------------------
if os.environ.get("KAGGLE_API_TOKEN"):
    import kagglehub
    dataset_dir = kagglehub.dataset_download(KAGGLE_DATASET)
    files = glob.glob(os.path.join(dataset_dir, "**", "*.xlsx"), recursive=True) + \
            glob.glob(os.path.join(dataset_dir, "**", "*.csv"), recursive=True)
    if not files:
        raise SystemExit(f"No .xlsx/.csv found in {dataset_dir}")
    DATA_PATH = Path(files[0])
elif LOCAL_DATA_PATH.exists():
    print(f"KAGGLE_API_TOKEN not set — using local copy at {LOCAL_DATA_PATH}")
    DATA_PATH = LOCAL_DATA_PATH
else:
    raise SystemExit(
        "Set KAGGLE_API_TOKEN (env var, or Colab Secret with notebook access on), "
        f"or place hospital_data_sampleee.xlsx at {LOCAL_DATA_PATH}."
    )
print(f"Loading {DATA_PATH}")

df = pd.read_csv(DATA_PATH) if DATA_PATH.suffix == ".csv" else pd.read_excel(DATA_PATH)
df.columns = df.columns.str.strip()
print(df.shape)
print(df.isnull().sum())

# -----------------------------
# TIME COLUMNS -> SECONDS SINCE MIDNIGHT
# -----------------------------
def time_to_seconds(t):
    try:
        h, m, s = str(t).strip().split(":")[:3]
        return int(h) * 3600 + int(m) * 60 + int(float(s))
    except Exception:
        return np.nan

for col in ["Entry Time", "Post-Consultation Time", "Completion Time"]:
    df[col + " (s)"] = df[col].apply(time_to_seconds)

df = df.dropna(subset=["Entry Time (s)", "Post-Consultation Time (s)", "Completion Time (s)"])
df["Date"] = pd.to_datetime(df["Date"])

entry = df["Entry Time (s)"]
post = df["Post-Consultation Time (s)"]
done = df["Completion Time (s)"]

# Handle visits that cross midnight
post = post.where(post >= entry, post + 86400)
done = done.where(done >= entry, done + 86400)
df["entry_s"], df["post_s"], df["done_s"] = entry, post, done

# -----------------------------
# TARGET
# -----------------------------
df["wait_min"] = (df["post_s"] - df["entry_s"]) / 60

# Sanity filter only (clearly broken records); real outliers are handled on train only
df = df[(df["wait_min"] > 0) & (df["wait_min"] <= 6 * 60)].copy()

# -----------------------------
# FEATURE ENGINEERING (arrival-time knowable only)
# -----------------------------
df["hour"] = (df["entry_s"] // 3600).astype(int)
df["minute_of_day"] = df["entry_s"] // 60
df["day_of_week"] = df["Date"].dt.dayofweek
df["is_weekend"] = (df["day_of_week"] >= 5).astype(int)

# Queue-load features: what queue-service knows at the moment someone joins
def add_load_features(day):
    e = day["entry_s"].to_numpy()
    d = day["done_s"].to_numpy()
    before = e[None, :] < e[:, None]                      # j arrived before i
    day = day.copy()
    day["patients_in_system"] = (before & (d[None, :] > e[:, None])).sum(axis=1)
    day["arrivals_last_60min"] = (before & (e[None, :] >= e[:, None] - 3600)).sum(axis=1)
    return day

df = pd.concat([add_load_features(g) for _, g in df.groupby("Date")])

NUMERIC = ["hour", "minute_of_day", "day_of_week", "is_weekend",
           "patients_in_system", "arrivals_last_60min"]
CATEGORICAL = ["Doctor Type", "Financial Class"]
FEATURES = NUMERIC + CATEGORICAL
# Not used: revenue/lab cost (only known after the visit), Patient Type (constant),
# Post-Consultation/Completion times (leak the target), Patient ID.

X = df[FEATURES]
y = df["wait_min"]

print("\nFeature matrix:", X.shape)
print("\nTarget (minutes):")
print(y.describe())

# -----------------------------
# SPLIT
# -----------------------------
X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, random_state=RANDOM_STATE
)

# IQR outlier removal on TRAIN only, so test metrics stay honest
q1, q3 = y_train.quantile([0.25, 0.75])
iqr = q3 - q1
mask = (y_train >= q1 - 1.5 * iqr) & (y_train <= q3 + 1.5 * iqr)
X_train, y_train = X_train[mask], y_train[mask]
print(f"\nTrain rows after IQR: {len(X_train)} | Test rows: {len(X_test)}")

# -----------------------------
# PREPROCESSING (inside each pipeline -> deployable as one object)
# -----------------------------
preprocessor = ColumnTransformer([
    ("num", Pipeline([
        ("imputer", SimpleImputer(strategy="median")),
        ("scaler", StandardScaler()),
    ]), NUMERIC),
    ("cat", Pipeline([
        ("imputer", SimpleImputer(strategy="most_frequent")),
        ("onehot", OneHotEncoder(handle_unknown="ignore")),
    ]), CATEGORICAL),
])

models = {
    "KNN": KNeighborsRegressor(n_neighbors=15),
    "LinearRegression": LinearRegression(),
    "DecisionTree": DecisionTreeRegressor(max_depth=8, min_samples_leaf=20, random_state=RANDOM_STATE),
    "RandomForest": RandomForestRegressor(n_estimators=200, min_samples_leaf=10,
                                          n_jobs=-1, random_state=RANDOM_STATE),
    "GradientBoosting": GradientBoostingRegressor(random_state=RANDOM_STATE),
}

# -----------------------------
# TRAIN & EVALUATE
# -----------------------------
baseline_mae = mean_absolute_error(y_test, np.full(len(y_test), y_train.mean()))
print(f"\nBaseline (predict train mean) MAE: {baseline_mae:.2f} min")

cv = KFold(n_splits=5, shuffle=True, random_state=RANDOM_STATE)
results, fitted = [], {}

for name, model in models.items():
    pipe = Pipeline([("prep", preprocessor), ("model", model)])
    cv_mae = -cross_val_score(pipe, X_train, y_train, cv=cv,
                              scoring="neg_mean_absolute_error", n_jobs=-1).mean()
    pipe.fit(X_train, y_train)
    pred = pipe.predict(X_test)

    mae = mean_absolute_error(y_test, pred)
    rmse = np.sqrt(mean_squared_error(y_test, pred))
    r2 = r2_score(y_test, pred)

    fitted[name] = pipe
    results.append({"Model": name, "CV_MAE": round(cv_mae, 2),
                    "MAE": round(mae, 2), "RMSE": round(rmse, 2), "R2": round(r2, 4)})

    print(f"\n{name}")
    print(f"  CV MAE : {cv_mae:.2f} min")
    print(f"  MAE    : {mae:.2f} min")
    print(f"  RMSE   : {rmse:.2f} min")
    print(f"  R2     : {r2:.4f}")
    print("-" * 35)

# -----------------------------
# SUMMARY + SAVE BEST
# -----------------------------
results_df = pd.DataFrame(results).sort_values("CV_MAE")
print("\n===== MODEL COMPARISON (minutes) =====")
print(results_df.to_string(index=False))
results_df.to_csv(COMPARISON_OUT, index=False)

best = results_df.iloc[0]["Model"]
joblib.dump({"pipeline": fitted[best], "features": FEATURES, "target": "wait_min"}, MODEL_OUT)
print(f"\nBest model by CV MAE: {best} -> saved to {MODEL_OUT}")

# Sidecar metrics/feature contract so app/routers/wait_time.py can report
# confidence/method without unpickling the pipeline just to read metadata.
best_row = results_df.iloc[0]
metadata = {
    "numeric_features": NUMERIC,
    "categorical_features": CATEGORICAL,
    "feature_order": FEATURES,
    "target": "wait_min",
    "target_unit": "minutes",
    "best_model": best,
    "test_mae_minutes": float(best_row["MAE"]),
    "test_rmse_minutes": float(best_row["RMSE"]),
    "test_r2": float(best_row["R2"]),
    "cv_mae_minutes": float(best_row["CV_MAE"]),
    "notes": (
        "hour/minute_of_day/day_of_week/is_weekend are computed server-side from "
        "the current timestamp when a citizen joins the queue. patients_in_system and "
        "arrivals_last_60min are live queue-load figures queue-service must supply at "
        "call time (they aren't derivable from a clock). Doctor Type/Financial Class "
        "are one-hot encoded inside the pipeline itself, with unknown categories at "
        "inference handled by OneHotEncoder(handle_unknown='ignore')."
    ),
}
with open(METADATA_OUT, "w") as f:
    json.dump(metadata, f, indent=2)
print(f"Saved metadata to {METADATA_OUT}")
