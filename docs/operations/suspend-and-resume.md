# Suspend & resume the dev environment

Pause the asoview-clone-dev GCP project to stop daily billing, then bring it back when you want to keep working. Written for the `dev` environment (`project_id = asoview-clone-dev`, `region = asia-northeast1`). Validated end-to-end on 2026-04-25.

## Where the money goes

April 2026 run rate showed ~¥48K / 23 days, of which six services were ~95% of the bill:

| Service | ¥/23d | Action |
|---|---|---|
| Compute Engine (GKE node pool VMs) | 20,840 | Goes with the GKE cluster |
| Cloud Spanner (1 processing unit min) | 6,722 | Must delete — no pause |
| Kubernetes Engine (control plane) | 5,727 | Goes with the GKE cluster |
| Cloud Build (CI minutes) | 5,031 | Disable triggers |
| Memorystore Redis (reserved capacity) | 3,722 | Must delete — no pause |
| Networking (LB, static IPs, egress) | 3,092 | Goes with GKE + LB orphan cleanup |
| Cloud SQL | 973 | Stop (preserves data) |
| Artifact Registry, Monitoring, Secret Manager | ~1,900 | Optional — see "Truly ¥0" below |

Disabling the Firebase/Identity Platform API key does **not** reduce any of these — it only blocks new logins. Billing is a function of provisioned resources, not keys.

## Before you suspend

1. Merge or stash any work in flight. The cluster will be gone after this.
2. One-time bucket + IAM setup for the Spanner GCS export (skip if already done — the actual export runs in suspend step 4 below, after the cluster is gone, so writes can't be lost mid-export):

   ```bash
   set -euo pipefail
   PROJECT=asoview-clone-dev
   REGION=asia-northeast1
   BUCKET=gs://asoview-clone-dev-spanner-backup

   gsutil mb -p $PROJECT -l $REGION $BUCKET
   gcloud services enable dataflow.googleapis.com --project=$PROJECT
   PROJECT_NUM=$(gcloud projects describe $PROJECT --format='value(projectNumber)')
   SA=${PROJECT_NUM}-compute@developer.gserviceaccount.com
   gcloud projects add-iam-policy-binding $PROJECT --member="serviceAccount:$SA" \
     --role=roles/spanner.databaseReader --condition=None
   gcloud projects add-iam-policy-binding $PROJECT --member="serviceAccount:$SA" \
     --role=roles/dataflow.worker --condition=None
   gsutil iam ch serviceAccount:$SA:objectAdmin $BUCKET
   # The Dataflow Service Agent role is normally auto-bound when the API is
   # enabled, but propagation can lag — first job often fails with "service
   # agent cannot access the worker service account". Force the binding:
   gcloud projects add-iam-policy-binding $PROJECT \
     --member="serviceAccount:service-${PROJECT_NUM}@dataflow-service-producer-prod.iam.gserviceaccount.com" \
     --role=roles/dataflow.serviceAgent --condition=None
   ```

3. Cloud SQL gets `--activation-policy=NEVER` below (data preserved automatically).
4. Redis has no data worth keeping in this project (it's a cache); nothing to back up.

## Suspend

Order matters: take the cluster down first so nothing is actively reading Spanner / Cloud SQL / Redis when you delete or stop them.

### 1. Disable Cloud Build triggers

`gcloud builds triggers update --disabled` is not a flag (only `--description` etc. are surfaced); the supported path is export → flip → import. The `sed` below replaces an existing `disabled: false` line in addition to appending when absent — a `grep -q` guard would silently leave `disabled: false` triggers enabled.

```bash
set -euo pipefail
PROJECT=asoview-clone-dev
TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT
for TRIGGER in $(gcloud builds triggers list --project=$PROJECT --format='value(id)'); do
  gcloud beta builds triggers export $TRIGGER --project=$PROJECT --destination=$TMP
  if grep -qE '^disabled:' $TMP; then
    sed -i.bak 's/^disabled:.*/disabled: true/' $TMP && rm -f "$TMP.bak"
  else
    echo "disabled: true" >> $TMP
  fi
  gcloud beta builds triggers import --project=$PROJECT --source=$TMP
done
gcloud builds triggers list --project=$PROJECT --format='table(name,disabled)'
```

### 2. Delete the GKE cluster

Deletes the cluster AND the LoadBalancer/forwarding rule plumbing the cluster owned. Takes ~10 min.

```bash
gcloud container clusters delete asoview-clone-dev \
  --location=asia-northeast1-a \
  --project=asoview-clone-dev \
  --quiet
```

(`location=asia-northeast1-a` because this is a zonal cluster, not regional. The exact zone is in `infra/terraform/environments/dev/main.tf` `module "gke"` block.)

### 3. Clean up orphan LB plumbing

GKE doesn't always reap every LB resource on cluster delete. The orphan forwarding rule keeps charging ~¥2K/mo if you skip this. Names are random hashes (cluster k8s service UIDs), so inventory first, then delete each by name.

```bash
PROJECT=asoview-clone-dev
REGION=asia-northeast1
echo "=== forwarding rules ==="; gcloud compute forwarding-rules list --project=$PROJECT
echo "=== target pools ==="; gcloud compute target-pools list --project=$PROJECT
echo "=== backend services ==="; gcloud compute backend-services list --project=$PROJECT
echo "=== HTTP health checks ==="; gcloud compute http-health-checks list --project=$PROJECT
echo "=== generic health checks ==="; gcloud compute health-checks list --project=$PROJECT
echo "=== k8s firewall rules ==="; gcloud compute firewall-rules list --project=$PROJECT --filter='name~^k8s' --format='value(name)'
```

For each name found, delete in this order (forwarding rule first frees the IP and allows the target/backend to be removed):

```bash
# Replace <NAME> with each hash from the inventory above.
gcloud compute forwarding-rules delete <NAME>   --region=$REGION --project=$PROJECT --quiet
gcloud compute target-pools delete <NAME>       --region=$REGION --project=$PROJECT --quiet
gcloud compute backend-services delete <NAME>   --region=$REGION --project=$PROJECT --quiet  # only if the inventory listed any
gcloud compute http-health-checks delete <NAME> --project=$PROJECT --quiet
gcloud compute health-checks delete <NAME>      --region=$REGION --project=$PROJECT --quiet  # newer L4 ILBs use regional health-checks
gcloud compute firewall-rules delete <NAME>     --project=$PROJECT --quiet                   # one or more `k8s-*` rules
```

### 4. Export Spanner to GCS

Run this AFTER the cluster is gone — no more writers, no risk of losing data between export-start and instance-delete. Spanner has no "stop" mode, so deleting is the only way to stop billing, and Spanner-native backups die with the instance, so a GCS export is the only durable copy.

The export uses the Dataflow `Cloud_Spanner_to_GCS_Avro` template. The custom VPC `asoview-clone-subnet` is required (the project has no default network), and the worker zone must be pinned to `asia-northeast1-a` because `-b`/`-c` regularly hit `ZONE_RESOURCE_POOL_EXHAUSTED` in the morning Tokyo window.

```bash
set -euo pipefail
PROJECT=asoview-clone-dev
REGION=asia-northeast1
BUCKET=gs://asoview-clone-dev-spanner-backup
STAMP=$(date +%Y%m%d-%H%M%S)

gcloud dataflow jobs run spanner-export-$STAMP \
  --project=$PROJECT --region=$REGION \
  --worker-zone=$REGION-a \
  --subnetwork=https://www.googleapis.com/compute/v1/projects/$PROJECT/regions/$REGION/subnetworks/asoview-clone-subnet \
  --worker-machine-type=e2-small \
  --max-workers=1 --num-workers=1 \
  --gcs-location=gs://dataflow-templates-$REGION/latest/Cloud_Spanner_to_GCS_Avro \
  --staging-location=$BUCKET/staging \
  --parameters="instanceId=asoview-clone-dev,databaseId=asoview,outputDir=$BUCKET/$STAMP/"

# Poll until terminal state. Typical run: 5-10 min for a small dev DB.
JOB_ID=$(gcloud dataflow jobs list --project=$PROJECT --region=$REGION \
  --filter="name=spanner-export-$STAMP" --format='value(id)' --limit=1)
while :; do
  STATE=$(gcloud dataflow jobs describe $JOB_ID --project=$PROJECT --region=$REGION --format='value(currentState)')
  echo "$STATE"
  case $STATE in JOB_STATE_DONE|JOB_STATE_FAILED|JOB_STATE_CANCELLED) break;; esac
  sleep 60
done

# Hard fail if the export didn't finish — without this, the next step deletes
# Spanner with no durable copy of the data.
if [ "$STATE" != "JOB_STATE_DONE" ]; then
  echo "Spanner export ended in $STATE — refusing to proceed." >&2
  exit 1
fi
# Sanity-check the manifest was actually written.
gsutil ls "$BUCKET/$STAMP/**/spanner-export.json" >/dev/null \
  || { echo "Export manifest missing — refusing to proceed." >&2; exit 1; }
```

### 5. Delete Spanner backups, then the instance

Spanner instance delete fails with `FAILED_PRECONDITION: Cannot delete instance ... as it contains backups` if any **Spanner-native** backups exist. (These are different from your GCS export.) Delete them first.

```bash
PROJECT=asoview-clone-dev
INSTANCE=asoview-clone-dev
for B in $(gcloud spanner backups list --instance=$INSTANCE --project=$PROJECT --format='value(name)'); do
  gcloud spanner backups delete "$B" --instance=$INSTANCE --project=$PROJECT --quiet
done
# (`--instance` and `--project` are required even when $B is the short name
# `asoview_<uuid>`; gcloud doesn't accept the full resource path here.)
gcloud spanner instances delete $INSTANCE --project=$PROJECT --quiet
```

### 6. Delete Memorystore Redis

```bash
gcloud redis instances delete asoview-clone-redis \
  --region=asia-northeast1 \
  --project=asoview-clone-dev \
  --quiet
```

### 7. Stop (don't destroy) Cloud SQL

Preserves the data disk at ~¥200/mo storage only. The Terraform module doesn't manage `activation_policy`, so the `gcloud` patch survives a no-op `terraform apply`.

```bash
gcloud sql instances patch asoview-clone-dev-pg \
  --activation-policy=NEVER \
  --project=asoview-clone-dev \
  --quiet
```

### 8. Verify

```bash
PROJECT=asoview-clone-dev
gcloud spanner instances list --project=$PROJECT
gcloud container clusters list --project=$PROJECT
gcloud redis instances list --region=asia-northeast1 --project=$PROJECT
gcloud sql instances list --project=$PROJECT --format='table(name,state)'
gcloud compute instances list --project=$PROJECT
gcloud compute forwarding-rules list --project=$PROJECT
```

All except Cloud SQL (state `STOPPED`) should be empty. If anything else is listed, it's still billing.

Wait ~24h, then check https://console.cloud.google.com/billing → Reports, group by service. Lines for Compute Engine / Spanner / Memorystore should be ¥0/day.

Expected residual after step 8: ~¥1500-2000/mo, mostly Artifact Registry image storage + Cloud SQL data disk + reserved external static IP + Secret Manager + monitoring baseline. ~97% reduction from a running environment.

## Truly ¥0 (optional, with resume cost)

The remaining ¥1500-2000/mo line items can also be removed, at the cost of a slower resume:

| Drop this | Saves | Resume cost |
|---|---|---|
| Artifact Registry repo `asoview-clone` (~44 GB images) | ~¥1100/mo | First Cloud Build after re-enable rebuilds all images (~30 min, ~¥1000 in build minutes — one-time) |
| Static external IP `asoview-clone-dev-edge` | ~¥500/mo | Re-point duckdns.org A records to the new IP after resume (5 min manual edit) |
| Cloud SQL instance (incl. data disk) | ~¥200/mo | Lose all Cloud SQL data; resume requires reseeding |

Commands:

```bash
PROJECT=asoview-clone-dev
gcloud artifacts repositories delete asoview-clone --location=asia-northeast1 --project=$PROJECT --quiet
gcloud compute addresses delete asoview-clone-dev-edge --region=asia-northeast1 --project=$PROJECT --quiet
# Cloud SQL: only do this if you are OK losing the data:
gcloud sql instances delete asoview-clone-dev-pg --project=$PROJECT --quiet
```

The first two are recommended for a long suspend (>1 month). Skip the Cloud SQL delete unless you have a separate dump.

## Resume

Reverse order. Budget ~30-60 min end-to-end (image rebuild dominates if you dropped Artifact Registry).

### 1. Reapply Terraform for the destroyed modules

Picks up GKE, Spanner, Redis, Artifact Registry, Static IP — whichever is missing. Idempotent on whatever still exists. Spanner schema is re-created empty by the module, ready for the import in step 3.

```bash
cd infra/terraform/environments/dev
terraform init   # restores .terraform/ if you wiped the workspace or are on a fresh clone
terraform apply -auto-approve
```

### 2. Start Cloud SQL

```bash
gcloud sql instances patch asoview-clone-dev-pg \
  --activation-policy=ALWAYS \
  --project=asoview-clone-dev \
  --quiet
```

### 3. Restore Spanner from the GCS export

The `GCS_Avro_to_Cloud_Spanner` template requires the destination database to **exist and be empty**. Step 1's `terraform apply` creates it empty, so this works on a clean resume; if a previous import partially populated it, drop and recreate the database before retrying (see "Recovery" below).

```bash
set -euo pipefail
PROJECT=asoview-clone-dev
REGION=asia-northeast1
BUCKET=gs://asoview-clone-dev-spanner-backup

# Pick the export dir to restore from. Sort by the YYYYMMDD-HHMMSS prefix
# rather than `tail -1` of `gsutil ls` (which doesn't guarantee order on
# bucket listings) and skip the staging dir explicitly.
EXPORT_DIR=$(gsutil ls $BUCKET/ | grep -v '/staging/' | grep -E '/[0-9]{8}-[0-9]{6}/$' | sort | tail -1)
JOB_DIR=$(gsutil ls "$EXPORT_DIR" | head -1)   # inner asoview-clone-dev-asoview-<job>/
echo "Restoring from $JOB_DIR"

STAMP=$(date +%Y%m%d-%H%M%S)
gcloud dataflow jobs run spanner-import-$STAMP \
  --project=$PROJECT --region=$REGION \
  --worker-zone=$REGION-a \
  --subnetwork=https://www.googleapis.com/compute/v1/projects/$PROJECT/regions/$REGION/subnetworks/asoview-clone-subnet \
  --worker-machine-type=e2-small \
  --max-workers=1 --num-workers=1 \
  --gcs-location=gs://dataflow-templates-$REGION/latest/GCS_Avro_to_Cloud_Spanner \
  --staging-location=$BUCKET/staging \
  --parameters="instanceId=asoview-clone-dev,databaseId=asoview,inputDir=$JOB_DIR"
```

Poll for `JOB_STATE_DONE` with the same loop as the export and abort if it ends in any other terminal state.

**Recovery if the import fails partway:** drop the half-populated database and recreate it via Terraform, then retry:

```bash
gcloud spanner databases delete asoview --instance=asoview-clone-dev --project=$PROJECT --quiet
cd infra/terraform/environments/dev && terraform apply -target=module.spanner -auto-approve
# Re-run the import job above.
```

If you don't have a backup, the schema lives in `services/*/src/main/resources/db/spanner/V*.sql` and Spring Boot bootstrap re-applies it on first deploy; data has to be reseeded by re-running the demo capture / E2E flows.

### 4. Re-enable Cloud Build triggers

Use the same export-edit-import pattern as the disable, with `sed` replacing `disabled: true` rather than appending:

```bash
set -euo pipefail
PROJECT=asoview-clone-dev
TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT
for TRIGGER in $(gcloud builds triggers list --project=$PROJECT --format='value(id)'); do
  gcloud beta builds triggers export $TRIGGER --project=$PROJECT --destination=$TMP
  sed -i.bak 's/^disabled:.*/disabled: false/' $TMP && rm -f "$TMP.bak"
  gcloud beta builds triggers import --project=$PROJECT --source=$TMP
done
gcloud builds triggers list --project=$PROJECT --format='table(name,disabled)'
```

### 5. Rebuild + redeploy

The GKE cluster delete also removed Argo CD itself. The cluster needs Argo bootstrapped again before any application Application resources sync. Bootstrap from the in-repo manifests:

```bash
gcloud container clusters get-credentials asoview-clone-dev \
  --location=asia-northeast1-a --project=asoview-clone-dev

# Install Argo CD core (namespace + CRDs + controllers) — fetch the version
# pinned in infra/argocd if there is one, or use the upstream stable release:
kubectl create namespace argocd 2>/dev/null || true
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Apply the app-of-apps root, which then syncs every per-service Application
# under infra/argocd/applications/.
kubectl apply -f infra/argocd/applications/_root.yaml
kubectl get applications -n argocd -w   # watch until everything syncs
```

If you kept Artifact Registry, Argo will pull existing image tags and deploy. If you dropped Artifact Registry, Argo will hang on `ImagePullBackOff` until images land — kick a build manually:

```bash
PROJECT=asoview-clone-dev
TRIGGER=$(gcloud builds triggers list --project=$PROJECT \
  --filter='name=asoview-clone-deploy' --format='value(id)')
gcloud builds triggers run $TRIGGER --branch=main --project=$PROJECT
# Wait ~30 min for all images to land, then Argo recovers from ImagePullBackOff.
```

Re-run any post-seed jobs:
- Vertex AI Search reindex: see `docs/operations/post-seed-vertex-reindex.md`
- BigQuery mart bootstrap: see `docs/operations/bigquery-mart-bootstrap.md`

### 6. Re-point DNS (only if you dropped the static IP)

A new external IP was provisioned by Terraform. Update the duckdns.org A records for `asoview-clone-dev` and `asoview-operator` to the new value:

```bash
gcloud compute addresses describe asoview-clone-dev-edge \
  --region=asia-northeast1 --project=asoview-clone-dev \
  --format='value(address)'
```

Then edit at https://www.duckdns.org. DNS propagation is ~1 min.

### 7. Smoke test

```bash
curl -fsS https://asoview-clone-dev.duckdns.org/api/v1/areas | head
TOKEN=$(curl -sS -X POST \
  "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$E2E_FIREBASE_API_KEY" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$E2E_TEST_EMAIL\",\"password\":\"$E2E_TEST_PASSWORD\",\"returnSecureToken\":true}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['idToken'])")
curl -fsS -H "Authorization: Bearer $TOKEN" \
  https://asoview-operator.duckdns.org/api/v1/op/me/venues | head
```

If both return 200 with real JSON, you're back.

## What stays running during a "near-zero" suspend

- Cloud SQL stopped (data disk only, ~¥200/mo)
- Static IPs (private VPC peering free; external `asoview-clone-dev-edge` ~¥500/mo if kept)
- Artifact Registry (~¥1100/mo if kept)
- Cloud DNS zones, IAM, Service Accounts, Secret Manager (~¥50/mo total)
- Workload Identity pool, Identity Platform config (free tier)
- Terraform state bucket + GCS buckets (pennies)

## When to nuke the project entirely

If you don't plan to resume for 3+ months and don't need the project name, `gcloud projects delete asoview-clone-dev` is the only way to guarantee ¥0. Drops Cloud DNS / Artifact Registry / Terraform state bucket / Spanner GCS export / IAM bindings — everything. Rebuild from scratch is ~1h including new billing link.
