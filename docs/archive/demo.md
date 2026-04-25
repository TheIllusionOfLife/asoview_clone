# asoview-clone Dev Environment — Demo Guide

One-page walkthrough for standing up the full `asoview-clone-dev`
environment on GCP and exercising the Asoview! consumer flow end-to-end.

## Overview

A single GKE Autopilot cluster in `asia-northeast1` hosts 8 namespaces:

| Namespace | Workloads |
|---|---|
| `edge` | `gateway`, `web`, ingress-nginx, Let's Encrypt cert |
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
2. `gcloud`, `kubectl`, `terraform >= 1.6`, `kustomize >= 5`, `argocd` CLI installed locally.
3. A **free DuckDNS subdomain** for HTTPS. Sign in at https://www.duckdns.org
   with GitHub or Google, claim `<name>.duckdns.org`, copy the token from the
   dashboard. **Store the token in a password manager — it is a secret**
   equivalent to a DNS admin password. The default committed hostname is
   `asoview-clone-dev.duckdns.org`.
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

# 4. Install + log in to the argocd CLI
#    macOS: brew install argocd
#    Linux: curl -sSL -o /usr/local/bin/argocd https://github.com/argoproj/argo-cd/releases/latest/download/argocd-linux-amd64 && chmod +x /usr/local/bin/argocd
#
#    The Argo CD API server is ClusterIP-only by default. Expose via
#    port-forward for this bootstrap.
kubectl port-forward -n argocd svc/argocd-server 8080:443 >/dev/null 2>&1 &
sleep 3
ARGOCD_PASSWORD=$(kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath='{.data.password}' | base64 -d)
argocd login localhost:8080 \
  --username admin \
  --password "$ARGOCD_PASSWORD" \
  --insecure

# 5. Bootstrap phase 1: patch the committed placeholders BEFORE any Argo
#    CD Application is applied. Argo CD auto-syncs on Application
#    creation, so applying ingress-nginx.yaml with EDIT_ME_STATIC_IP in
#    place provisions a LoadBalancer against an invalid IP and wedges
#    the stack. Patch-then-apply is the explicit Terraform → GitOps bridge.
STATIC_IP=$(terraform -chdir=infra/terraform/environments/dev output -raw static_ip)
echo "Static IP: $STATIC_IP"
LETSENCRYPT_EMAIL="<your-email@example.com>"  # <-- edit before running
sed -i.bak "s|EDIT_ME_STATIC_IP|$STATIC_IP|" infra/argocd/applications/ingress-nginx.yaml
sed -i.bak "s|REPLACE_WITH_ACME_EMAIL|$LETSENCRYPT_EMAIL|" infra/k8s/edge/clusterissuer.yaml
rm infra/argocd/applications/ingress-nginx.yaml.bak infra/k8s/edge/clusterissuer.yaml.bak
git add infra/argocd/applications/ingress-nginx.yaml infra/k8s/edge/clusterissuer.yaml
git commit -m "chore(deploy): pin static IP + letsencrypt email for dev"
git push origin main

# 6. Set the DuckDNS A record. The token is a SECRET — read it silently
#    so it doesn't land in shell history, and unset it after use.
DUCKDNS_SUBDOMAIN="asoview-clone-dev"  # <-- the subdomain you claimed
read -rs -p "DuckDNS token: " DUCKDNS_TOKEN; echo
curl "https://www.duckdns.org/update?domains=${DUCKDNS_SUBDOMAIN}&token=${DUCKDNS_TOKEN}&ip=${STATIC_IP}"
#    Expect "OK".
unset DUCKDNS_TOKEN

# 7. Apply the Argo CD Applications in strict order. syncWave annotations
#    do NOT order across separate Applications applied directly, so the
#    ordering is enforced manually here: cert-manager first (installs
#    CRDs), then ingress-nginx (provides the Service on the static IP),
#    then the edge app (ClusterIssuer + Ingress), then the 7 service apps.
kubectl apply -f infra/argocd/applications/cert-manager.yaml
kubectl wait --for=condition=Available deployment -l app.kubernetes.io/instance=cert-manager -n cert-manager --timeout=5m

kubectl apply -f infra/argocd/applications/ingress-nginx.yaml
kubectl wait --for=condition=Available deployment -l app.kubernetes.io/instance=ingress-nginx -n edge --timeout=5m
kubectl get svc -n edge ingress-nginx-controller -o wide
#    EXTERNAL-IP should match $STATIC_IP

kubectl apply -f infra/argocd/applications/edge.yaml
kubectl apply -f infra/argocd/applications/commerce-core.yaml \
               -f infra/argocd/applications/gateway.yaml \
               -f infra/argocd/applications/web.yaml \
               -f infra/argocd/applications/ticketing-service.yaml \
               -f infra/argocd/applications/reservation-service.yaml \
               -f infra/argocd/applications/ads-service.yaml \
               -f infra/argocd/applications/analytics-ingest.yaml \
               -f infra/argocd/applications/search.yaml

# 8. Wait for Let's Encrypt to issue the TLS cert (usually ~2 min).
kubectl wait --for=condition=Ready certificate/asoview-clone-tls -n edge --timeout=10m
kubectl describe certificate asoview-clone-tls -n edge  # inspect if stuck

# 9. Visit https://${DUCKDNS_SUBDOMAIN}.duckdns.org/ja
```

### Let's Encrypt rate limits

Two limits apply:

- **50 certificates per registered domain per week**: `duckdns.org` is the
  registered domain, so the bucket is **shared across every DuckDNS user**.
  Our renewal cadence is every 60 days (~6 certs/year), so we barely touch
  the limit on our own, but heavy DuckDNS usage from other users CAN
  exhaust it. If hit, cert-manager reports the error and retries after the
  window clears.
- **5 duplicate certificates per identical identifier set per week**: this
  is the one you hit during debugging if you delete and recreate the
  `asoview-clone-tls` Certificate. Mitigation: when debugging, set
  `spec.acme.server` on the ClusterIssuer to the staging URL
  `https://acme-staging-v02.api.letsencrypt.org/directory`, which has
  much higher limits but issues untrusted certs (browser warning). Flip
  back to production once the flow works end-to-end.
- **Fallback if blocked**: install `cert-manager-webhook-duckdns` and
  switch the ClusterIssuer from HTTP-01 to DNS-01. DNS-01 uses a
  separate rate-limit bucket and works around the shared
  `duckdns.org` problem. Not shipped in this PR; deferred follow-up.

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
terraform -chdir=infra/terraform/environments/dev destroy
```

This removes the GKE cluster, Cloud SQL, Spanner, Memorystore, and the
regional static IP. The DuckDNS subdomain stays (free, no cleanup
required). If you want to un-point it, log in to duckdns.org and
delete the subdomain, or update its A record to `0.0.0.0`.

## Estimated Monthly Cost

| Resource | ~USD / month |
|---|---|
| GKE Autopilot baseline (8 small pods + ingress-nginx) | \$75 |
| Cloud SQL `db-f1-micro` | \$10 |
| Cloud Spanner 100 PU | \$65 |
| Memorystore Redis basic (1 GB) | \$30 |
| Regional L4 LB (ingress-nginx Service) + static IP | \$18 |
| TLS certs (Let's Encrypt) | \$0 |
| DuckDNS subdomain | \$0 |
| **Total** | **\~\$200/mo** |

Cost-saving options when the demo is idle: Cloud Spanner's minimum is
100 PU and instances cannot be paused, so the only way to stop Spanner
charges is to `terraform destroy` the instance (or delete it via
`gcloud spanner instances delete`) and re-create it for the next demo
session. Autoscaling is an option for variable workloads but its own
minimum is 1 node / 1000 PU, which is more expensive than the 100 PU
floor we use here. The full ~$200/mo figure above assumes the stack
runs continuously.
