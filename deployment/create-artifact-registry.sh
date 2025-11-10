#!/usr/bin/env sh

# must be run before deployment

NAME="hf-bt"

gcloud artifacts repositories create "${NAME}" --repository-format=docker --location=us-central1 --project="${GCP_PROJECT_ID}"