# Deployment Guide

This guide describes how to deploy the hf-bt application to Google Cloud Run.

## Deployment Overview

The hf-bt application is deployed to Google Cloud Run using a multi-step process:

1. Build a Docker image with Jib (Google's Java Image Builder)
2. Push the image to Google Cloud Artifact Registry
3. Deploy the image to Cloud Run

## Prerequisites

Before deploying, ensure you have:

- **Google Cloud SDK** installed and configured
- **Active GCP credentials** (authenticated with `gcloud auth login`)
- **A GCP project** with Cloud Run and Artifact Registry APIs enabled
- **GCP_PROJECT_ID** environment variable set (or provide it when running the deployment script)

### Verify GCP Setup

```bash
# Check authenticated accounts
gcloud auth list

# Check your current GCP project
gcloud config get-value project

# Set the project if needed
gcloud config set project YOUR_PROJECT_ID
```

## Deployment Steps

### Step 1: Create Artifact Registry Repository (One-time Setup)

Before the first deployment, create a Docker registry in Artifact Registry:

```bash
gcloud artifacts repositories create hf-bt \
  --repository-format=docker \
  --location=us-central1 \
  --project=YOUR_PROJECT_ID
```

This creates a repository at: `us-central1-docker.pkg.dev/YOUR_PROJECT_ID/hf-bt/hf-bt`

### Step 2: Run the Deployment Script

Execute the main deployment script:

```bash
GCP_PROJECT_ID=YOUR_PROJECT_ID bash deployment/deploy.sh
```

This script performs the following operations:

1. **Build Docker image with Jib**
   ```bash
   ./gradlew jibBuildTar --no-configuration-cache
   ```
   - Builds the Spring Boot application as a Docker image
   - Outputs to `build/jib-image.tar`

2. **Load image into Docker daemon**
   ```bash
   docker load --input build/jib-image.tar
   ```
   - Loads the tar-based image so it can be pushed to a registry

3. **Tag image for Artifact Registry**
   ```bash
   docker tag hf-bt:latest us-central1-docker.pkg.dev/$GCP_PROJECT_ID/hf-bt/hf-bt:latest
   ```

4. **Push to Artifact Registry**
   ```bash
   docker push us-central1-docker.pkg.dev/$GCP_PROJECT_ID/hf-bt/hf-bt:latest
   ```

5. **Deploy to Cloud Run**
   ```bash
   gcloud run deploy hf-bt \
     --region=us-central1 \
     --platform=managed \
     --allow-unauthenticated \
     --port=8080 \
     --image=us-central1-docker.pkg.dev/$GCP_PROJECT_ID/hf-bt/hf-bt:latest \
     --min-instances=0 \
     --max-instances=3 \
     --cpu=1 \
     --memory=512M
   ```

## Deployment Configuration

### Cloud Run Service Configuration

| Parameter | Value | Description |
|-----------|-------|-------------|
| Service Name | `hf-bt` | Name of the Cloud Run service |
| Region | `us-central1` | Deployment region |
| Platform | `managed` | Google-managed Cloud Run |
| Port | `8080` | Container port exposed by the application |
| CPU | `1` | 1 vCPU allocated to each instance |
| Memory | `512M` | 512MB RAM per instance |
| Min Instances | `0` | Scale down to zero when idle |
| Max Instances | `3` | Maximum 3 concurrent instances |
| Authentication | Unauthenticated | Public access allowed |

### Jib Configuration

Jib build configuration is defined in `build.gradle.kts`:

- **Base Image**: `eclipse-temurin:21-jre`
- **Main Class**: `ltd.gsio.hfbt.HfBtApplicationKt`
- **Container Port**: `8080`
- **JVM Flags**: `-Xms512m -Xmx1024m` (512MB min, 1GB max heap)

## Accessing the Deployed Service

After successful deployment, the service URL will be displayed:

```
Service URL: https://hf-bt-XXXXXXXXXX.us-central1.run.app
```

### Example Request

```bash
curl https://hf-bt-XXXXXXXXXX.us-central1.run.app/health
```

### Monitoring and Management

Access Cloud Run management via:

```bash
# View service details
gcloud run services describe hf-bt --region=us-central1

# View recent deployments
gcloud run services list-revisions hf-bt --region=us-central1

# View logs
gcloud run services logs read hf-bt --region=us-central1 --limit=50

# Open Cloud Console
gcloud run services describe hf-bt --region=us-central1 --format='value(status.url)'
```

## Environment Variables

If your application requires environment variables, add them to the Cloud Run service:

```bash
gcloud run services update hf-bt \
  --region=us-central1 \
  --set-env-vars KEY1=value1,KEY2=value2
```

## Troubleshooting

### Artifact Registry Repository Not Found

If you see `Repository "hf-bt" not found`, create it first:

```bash
gcloud artifacts repositories create hf-bt \
  --repository-format=docker \
  --location=us-central1 \
  --project=YOUR_PROJECT_ID
```

### GCP Authentication Issues

```bash
# Re-authenticate
gcloud auth login

# Set the correct project
gcloud config set project YOUR_PROJECT_ID
```

### Deployment Fails at Docker Push

Ensure Docker daemon is running and you have Artifact Registry API enabled:

```bash
gcloud services enable artifactregistry.googleapis.com
gcloud services enable run.googleapis.com
```

### Check Cloud Run Service Status

```bash
gcloud run services describe hf-bt --region=us-central1
```

## Alternative Deployment Methods

### Local Docker Compose (Development)

For local development without GCP:

```bash
docker-compose up
```

See `docker-compose.yml` for configuration.

### Cloud Build CI/CD

For automated deployments on source code commits, use Google Cloud Build:

See `cloudbuild.yaml` for the CI/CD pipeline configuration.

## Rollback

To rollback to a previous revision:

```bash
# List available revisions
gcloud run revisions list --service=hf-bt --region=us-central1

# Direct traffic to a previous revision
gcloud run services update-traffic hf-bt \
  --to-revisions=hf-bt-00001-XXXXX=100 \
  --region=us-central1
```

## Next Steps

- Monitor application logs and metrics in Cloud Run console
- Set up custom domains if needed
- Configure Cloud Run security settings (VPC, identity)
- Set up automated deployments via Cloud Build