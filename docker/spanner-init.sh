#!/usr/bin/env bash
#
# Initializes the local Cloud Spanner emulator for commerce-core development:
#   1. waits for the emulator gRPC endpoint to become reachable
#   2. creates the target instance (idempotent)
#   3. creates the target database (idempotent)
#   4. applies all V*.sql DDL files from the mounted db/spanner directory
#
# Intended to be run from the docker-compose "spanner-init" service using the
# google/cloud-sdk:slim image. Requires the following environment variables:
#
#   SPANNER_EMULATOR_HOST  host:port of the emulator (e.g. spanner-emulator:9020)
#   SPANNER_PROJECT_ID     GCP project id (e.g. asoview-local)
#   SPANNER_INSTANCE_ID    instance id (e.g. asoview-local)
#   SPANNER_DATABASE_ID    database id (e.g. commerce)
#   SPANNER_DDL_DIR        directory containing V*.sql files (mounted)

set -euo pipefail

: "${SPANNER_EMULATOR_HOST:?SPANNER_EMULATOR_HOST must be set}"
: "${SPANNER_PROJECT_ID:=asoview-local}"
: "${SPANNER_INSTANCE_ID:=asoview-local}"
: "${SPANNER_DATABASE_ID:=commerce}"
: "${SPANNER_DDL_DIR:=/ddl}"

export CLOUDSDK_AUTH_DISABLE_CREDENTIALS=true
export CLOUDSDK_API_ENDPOINT_OVERRIDES_SPANNER="http://${SPANNER_EMULATOR_HOST%:*}:9020/"
export CLOUDSDK_CORE_PROJECT="${SPANNER_PROJECT_ID}"

echo "[spanner-init] waiting for emulator at ${SPANNER_EMULATOR_HOST}..."
for _ in $(seq 1 60); do
  if curl -sf "http://${SPANNER_EMULATOR_HOST%:*}:9020/v1/projects/${SPANNER_PROJECT_ID}/instanceConfigs" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo "[spanner-init] ensuring instance ${SPANNER_INSTANCE_ID}..."
if ! gcloud spanner instances describe "${SPANNER_INSTANCE_ID}" >/dev/null 2>&1; then
  gcloud spanner instances create "${SPANNER_INSTANCE_ID}" \
    --config=emulator-config \
    --description="asoview local" \
    --nodes=1
fi

echo "[spanner-init] ensuring database ${SPANNER_DATABASE_ID}..."
if ! gcloud spanner databases describe "${SPANNER_DATABASE_ID}" \
      --instance="${SPANNER_INSTANCE_ID}" >/dev/null 2>&1; then
  gcloud spanner databases create "${SPANNER_DATABASE_ID}" \
    --instance="${SPANNER_INSTANCE_ID}"
fi

# Apply DDL files in lexicographic order (V1..V9__*.sql). Each file can contain
# multiple statements separated by semicolons; gcloud's --ddl-file accepts the
# full file.
shopt -s nullglob
ddl_files=("${SPANNER_DDL_DIR}"/V*.sql)
if [ ${#ddl_files[@]} -eq 0 ]; then
  echo "[spanner-init] no DDL files found in ${SPANNER_DDL_DIR}; skipping"
  exit 0
fi

for ddl in "${ddl_files[@]}"; do
  echo "[spanner-init] applying $(basename "${ddl}")"
  gcloud spanner databases ddl update "${SPANNER_DATABASE_ID}" \
    --instance="${SPANNER_INSTANCE_ID}" \
    --ddl-file="${ddl}" || {
      echo "[spanner-init] DDL from $(basename "${ddl}") may already be applied, continuing"
    }
done

echo "[spanner-init] done"
