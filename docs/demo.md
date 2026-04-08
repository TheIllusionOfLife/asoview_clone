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

All commands below run from the **repo root**. The terraform commands
use `-chdir=...` so they don't change the shell's working directory.

```bash
# 1. Provision infrastructure
cp infra/terraform/environments/dev/terraform.tfvars.example \
   infra/terraform/environments/dev/terraform.tfvars   # edit project_id, region, domain
terraform -chdir=infra/terraform/environments/dev init
terraform -chdir=infra/terraform/environments/dev apply

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

# 3b. Workload Identity for commerce-core is created by terraform
#     (infra/terraform/environments/dev/workload-identity.tf): GSA,
#     project IAM bindings (cloudsql.client + spanner.databaseUser),
#     and the workloadIdentityUser binding linking the KSA
#     `core-services/commerce-core` to the GSA. The kustomize dev
#     overlay then patches the iam.gke.io/gcp-service-account
#     annotation on the KSA with the email exposed by the terraform
#     output `commerce_core_gsa_email`. If you change the dev project
#     id, sync that overlay patch + the terraform variable.

# 4. Bootstrap Argo CD Applications (run from repo root)
kubectl apply -f infra/argocd/applications/

# 5. Install + log in to the argocd CLI
#    macOS: brew install argocd
#    Linux: curl -sSL -o /usr/local/bin/argocd https://github.com/argoproj/argo-cd/releases/latest/download/argocd-linux-amd64 && chmod +x /usr/local/bin/argocd
#
#    The Argo CD API server is ClusterIP-only by default. Either expose
#    it via port-forward (simplest) or via a LoadBalancer Service.
kubectl port-forward -n argocd svc/argocd-server 8080:443 >/dev/null 2>&1 &
sleep 3
ARGOCD_PASSWORD=$(kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath='{.data.password}' | base64 -d)
argocd login localhost:8080 \
  --username admin \
  --password "$ARGOCD_PASSWORD" \
  --insecure

# 6. Wait for initial sync (~5 min). The selector matches the
#    `app.kubernetes.io/part-of: asoview-clone-dev` label that every
#    Application manifest under infra/argocd/applications/ carries.
argocd app sync -l app.kubernetes.io/part-of=asoview-clone-dev
argocd app wait -l app.kubernetes.io/part-of=asoview-clone-dev --health

# 7. Configure DNS
#    Get the static IP:
terraform -chdir=infra/terraform/environments/dev output static_ip
#    Create an A record in your DNS provider:
#      <your-domain>.  300  IN  A  <static-ip>

# 8. Activate the edge ingress with your real domain
#    The base infra/k8s/edge/ directory ships EMPTY (kustomization.yaml
#    has resources: []) so Argo CD does NOT sync the example.com
#    placeholder. To activate:
#      a. Copy infra/k8s/edge/template/ingress.yaml + managed-certificate.yaml
#         into infra/k8s/edge/
#      b. Replace `example.com` with your real domain in both files
#      c. Add `host: <your-domain>` to the Ingress rules
#      d. List both filenames in infra/k8s/edge/kustomization.yaml's
#         resources: array
#      e. Commit and push — Argo CD picks it up
#    Wait ~15 min for ManagedCertificate to provision the cert.

# 8. Visit https://<your-domain>/ja
```

## Redeploy (after code changes)

`cloudbuild.yaml` is **trigger-only**. Local `gcloud builds submit`
will not work because it doesn't populate `$SHORT_SHA` or `$BRANCH_NAME`
and uploads a tarball without `.git` metadata. Create a GitHub trigger
once, then redeploys are automatic on every push to `main`.

```bash
# One-time trigger setup. Sensitive substitutions (FIREBASE_API_KEY,
# STRIPE_PUBLISHABLE_KEY) should live in Secret Manager and be wired
# in via the Cloud Console UI or `gcloud builds triggers update` with
# --substitutions referencing $$SECRET notation. Plain --substitutions
# below is shown for brevity; do NOT use the literal command in CI
# logs because the values land in shell history + build logs.
gcloud builds triggers create github \
  --name=asoview-clone-deploy \
  --repo-name=asoview_clone --repo-owner=TheIllusionOfLife \
  --branch-pattern='^main$' \
  --build-config=cloudbuild.yaml \
  --substitutions=_DOMAIN=<your-domain>,_FIREBASE_PROJECT_ID=asoview-clone-dev
```

Every push to `main` then fires the trigger, builds and pushes 7
images in parallel to Artifact Registry, bumps the image tags in
`infra/k8s/*/overlays/dev/kustomization.yaml`, and commits back to
`main` (with `[skip ci]` so the bump itself doesn't loop). Argo CD
reconciles within its sync window.

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
