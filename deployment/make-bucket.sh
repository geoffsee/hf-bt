#!/usr/bin/env sh

set -e  # Exit on error

# Default values
REGION=${REGION:-us-central1}
BUCKET_NAME=${BUCKET_NAME:-hf-bt-storage-${GCP_PROJECT_ID}}

echo "Creating GCS bucket: ${BUCKET_NAME}"
gcloud storage buckets create gs://${BUCKET_NAME} \
  --project=${GCP_PROJECT_ID} \
  --location=${REGION} \
  --uniform-bucket-level-access

echo "Bucket created successfully: gs://${BUCKET_NAME}"
echo "Use this bucket name in your Cloud Build trigger with: --substitutions=_BUCKET=${BUCKET_NAME}"