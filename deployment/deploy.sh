#!/usr/bin/env sh

set -e  # Exit on error

echo "Building Docker image with Jib..."
./gradlew jibBuildTar --no-configuration-cache

echo "Loading Docker image from tar..."
docker load --input build/jib-image.tar

echo "Tagging image for GCP Artifact Registry..."
docker tag hf-bt:latest us-central1-docker.pkg.dev/$GCP_PROJECT_ID/hf-bt/hf-bt:latest

echo "Pushing image to GCP Artifact Registry..."
docker push us-central1-docker.pkg.dev/$GCP_PROJECT_ID/hf-bt/hf-bt:latest

echo "Deploying to Cloud Run..."
gcloud run deploy hf-bt --region=us-central1 --platform=managed --allow-unauthenticated \
      --port=8080 --image=us-central1-docker.pkg.dev/$GCP_PROJECT_ID/hf-bt/hf-bt:latest \
      --min-instances=0 --max-instances=3 --cpu=1 --memory=512M

echo "Deployment complete!"