# asoview-clone Dev Environment — Demo Guide

One-page walkthrough for standing up the full `asoview-clone-dev`
environment on GCP and exercising the Asoview! consumer flow end to end.

## Overview

A single GKE Autopilot cluster in `asia-northeast1` hosts 8 namespaces:

| Namespace | Workloads |
|---|---|
| `edge` | `gateway`, `web`, GKE Ingress + ManagedCertificate |
| `core-services` | `commerce-core`, `ticketing-service`, `reservation-service` |
| `ads-services` | `ads-service` |
| `data-jobs` | `analytics-ingest` |
| `search` | `opensearch`, `opensearch-dashboards`, `search-service` |
| `consumer-web` | (future) additional consumer apps |
| `operator-web` | (future) operator consoles |
| `observability` | (future) Prometheus / Grafana / Loki |

Backing services: Cloud SQL (Postgres 16, `db-f1-micro`), Cloud Spanner
(100 PU), Memorystore Redis basic, Pub/Sub, Artifact Registry, Identity
Platform, Cloud Build.

## Prerequisites

1. GCP project `asoview-clone-dev` with billing enabled.
2. `gcloud`, `kubectl`, `terraform >= 1.6`, `kustomize >= 5` installed locally.
3. A registered domain (\~\$10/yr) for HTTPS. GKE ManagedCertificate
   requires DNS-validated ownership.
4. Argo CD installed in the cluster's `argocd` namespace. If not yet:
   `kubectl create namespace argocd && kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml`

## First-time Deploy

```bash
# 1. Provision infrastructure
cd infra/terraform/environments/dev
cp terraform.tfvars.example terraform.tfvars   # edit project_id, region, domain
terraform init
terraform apply

# 2. Wire kubectl to the new cluster
gcloud container clusters get-credentials asoview-clone-dev \
  --region asia-northeast1 --project asoview-clone-dev

# 3. Create application secrets (namespaces auto-created by Argo CD)
kubectl create namespace core-services || true
kubectl create namespace edge || true

kubectl create secret generic cloudsql-creds \
  --namespace core-services \
  --from-literal=username=asoview \
  --from-literal=password='<generated-strong-password>'

kubectl create configmap cloudsql-config \
  --namespace core-services \
  --from-literal=connection-name='asoview-clone-dev:asia-northeast1:asoview-clone-dev'

kubectl create secret generic stripe-test \
  --namespace core-services \
  --from-literal=secret-key='sk_test_...' \
  --from-literal=webhook-secret='whsec_...'

kubectl create secret generic firebase-config \
  --namespace edge \
  --from-literal=api-key='...' \
  --from-literal=project-id='asoview-clone-dev'

# 4. Bootstrap Argo CD Applications
kubectl apply -f infra/argocd/applications/

# 5. Wait for initial sync (~5 min)
argocd app sync -l argocd.argoproj.io/instance=default
argocd app wait -l argocd.argoproj.io/instance=default --health

# 6. Configure DNS
#    Get the static IP:
terraform output static_ip
#    Create an A record in your DNS provider:
#      <your-domain>.  300  IN  A  <static-ip>

# 7. Update the ManagedCertificate + Ingress to use your domain
#    Edit infra/k8s/edge/managed-certificate.yaml (replace example.com),
#    commit and push. Argo CD picks up the change. Wait ~15 min for
#    Google to issue the cert.

# 8. Visit https://<your-domain>/ja
```

## Redeploy (after code changes)

```bash
gcloud builds submit --config cloudbuild.yaml \
  --substitutions=_DOMAIN=<your-domain>,_FIREBASE_API_KEY=...,_FIREBASE_AUTH_DOMAIN=...,_FIREBASE_PROJECT_ID=asoview-clone-dev,_FIREBASE_APP_ID=...,_STRIPE_PUBLISHABLE_KEY=pk_test_...
```

Cloud Build builds 7 images in parallel, pushes them to Artifact
Registry, bumps the image tags in `infra/k8s/*/overlays/dev/kustomization.yaml`,
and commits back to `main`. Argo CD reconciles within its sync window.

## Stripe Test Cards

| Card | Outcome |
|---|---|
| `4242 4242 4242 4242` | Success, no 3DS |
| `4000 0027 6000 3184` | Requires 3DS challenge (interactive flow) |
| `4000 0000 0000 9995` | Declined — insufficient funds |

Any future expiry, any 3-digit CVC, any postal code.

## Tear Down

```bash
cd infra/terraform/environments/dev
terraform destroy
```

This removes the GKE cluster, Cloud SQL, Spanner, Memorystore, and the
static IP. DNS A records must be deleted manually from your DNS provider.

## Estimated Monthly Cost

| Resource | ~USD / month |
|---|---|
| GKE Autopilot baseline (8 small pods) | \$75 |
| Cloud SQL `db-f1-micro` | \$10 |
| Cloud Spanner 100 PU | \$65 |
| Memorystore Redis basic (1 GB) | \$30 |
| Load Balancer + static IP | \$20 |
| **Total** | **\~\$200/mo** |

Scale Spanner to 0 PU or pause the cluster when not in active use to cut
roughly half the cost.
