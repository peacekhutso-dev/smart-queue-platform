FROM python:3.11-slim

# System deps for OpenCV + DeepFace
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

RUN groupadd -r appuser && useradd -r -g appuser -m -d /home/appuser appuser
ENV HOME=/home/appuser

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Pre-download Facenet512 model weights at build time (lands under $HOME/.deepface)
RUN python -c "from deepface import DeepFace; DeepFace.build_model('Facenet512')"

COPY . .
RUN chown -R appuser:appuser /app /home/appuser

USER appuser

EXPOSE 8002
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8002"]
