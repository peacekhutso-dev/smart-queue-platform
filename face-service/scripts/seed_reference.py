"""
Testing utility only — not part of the running service.

Seeds a reference face embedding for a given userId directly from an image
file (e.g. a photo of your DHA ID), simulating the real Department of Home
Affairs setup where your photo is already on file. Without this, the first
live scan through the app silently becomes your reference instead (see
main.py's docstring) — fine for a quick smoke test, but it doesn't exercise
an actual "compare against an existing photo" verification.

Usage (run inside the face-service container, so it shares main.py's exact
DeepFace/Redis setup):

    docker compose cp your_id_photo.jpg face-service:/tmp/id_photo.jpg
    docker compose cp scripts/seed_reference.py face-service:/tmp/seed_reference.py
    docker compose exec -w /app face-service python3 /tmp/seed_reference.py <userId> /tmp/id_photo.jpg

<userId> is the UUID identity-service assigned you at registration (visible
in the browser's cookie-free JSON response from /api/auth/me, or query the
identity_db.users table directly).
"""
import base64
import json
import sys

from main import decode_image, get_embedding, redis_client, EMBEDDING_KEY_PREFIX


def seed(user_id: str, image_path: str) -> None:
    with open(image_path, "rb") as f:
        raw = f.read()
    b64 = base64.standard_b64encode(raw).decode("utf-8")

    img = decode_image(b64)
    embedding = get_embedding(img)

    key = f"{EMBEDDING_KEY_PREFIX}{user_id}"
    redis_client.set(key, json.dumps(embedding))
    print(f"Seeded reference embedding for userId={user_id} from {image_path}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python3 seed_reference.py <userId> <image_path>")
        sys.exit(1)
    seed(sys.argv[1], sys.argv[2])
