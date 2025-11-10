#!/usr/bin/env sh

set -e  # Exit on error

echo "Checking GCP authentication status..."

# Check if user is logged in
if ! gcloud auth list --filter=status:ACTIVE --format="value(account)" | grep -q "@"; then
    echo "No active GCP authentication found. Please log in."
    gcloud auth login
else
    ACTIVE_ACCOUNT=$(gcloud auth list --filter=status:ACTIVE --format="value(account)" | head -n 1)
    echo "Already authenticated as: ${ACTIVE_ACCOUNT}"
fi

# Check if GCP_PROJECT_ID is set
if [ -z "${GCP_PROJECT_ID:-}" ]; then
    echo "ERROR: GCP_PROJECT_ID environment variable is not set."
    echo "Please set it with: export GCP_PROJECT_ID=your-project-id"
    exit 1
fi

# Verify the project exists and set it as active
echo "Verifying GCP project: ${GCP_PROJECT_ID}"
if gcloud projects describe "${GCP_PROJECT_ID}" > /dev/null 2>&1; then
    gcloud config set project "${GCP_PROJECT_ID}"
    echo "GCP project set to: ${GCP_PROJECT_ID}"
else
    echo "ERROR: Project ${GCP_PROJECT_ID} not found or you don't have access to it."
    exit 1
fi

# Configure Docker authentication for Artifact Registry
echo "Configuring Docker authentication for GCP Artifact Registry..."
gcloud auth configure-docker us-central1-docker.pkg.dev --quiet

echo "Platform login complete! You are ready to run deployment scripts."
