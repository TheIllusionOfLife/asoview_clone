#!/usr/bin/env bash
# End-to-end smoke for the local/dev seed dataset. Brings up docker-compose
# infra, boots commerce-core in the background with seed_catalog=true, waits
# for readiness, and asserts that /v1/areas, /v1/products, and /availability
# return non-empty results.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

RUN_ID="$(date +%s)-$$"
LOG="/tmp/seed-smoke-${RUN_ID}.log"
PID_FILE="/tmp/seed-smoke-${RUN_ID}.pid"

cleanup() {
  if [[ -f "$PID_FILE" ]]; then
    local pid
    pid=$(cat "$PID_FILE" || true)
    if [[ -n "${pid:-}" ]] && kill -0 "$pid" 2>/dev/null; then
      echo "seed-smoke: stopping commerce-core (pid=$pid)"
      kill "$pid" 2>/dev/null || true
      for _ in 1 2 3 4 5; do
        sleep 1
        kill -0 "$pid" 2>/dev/null || break
      done
      if kill -0 "$pid" 2>/dev/null; then
        echo "seed-smoke: SIGTERM didn't take, escalating to SIGKILL"
        kill -9 "$pid" 2>/dev/null || true
      fi
    fi
    rm -f "$PID_FILE"
  fi
}
trap cleanup EXIT

echo "seed-smoke: bringing up docker-compose infra"
docker compose up -d postgres redis spanner-emulator

echo "seed-smoke: running spanner-init synchronously"
# Run as a one-shot foreground container so the database exists BEFORE
# bootRun starts. Re-running on a primed emulator is a fast no-op because
# spanner-init.sh treats `ALREADY_EXISTS` as success.
docker compose run --rm spanner-init

echo "seed-smoke: waiting for postgres"
for i in $(seq 1 60); do
  if docker compose exec -T postgres pg_isready -U asoview >/dev/null 2>&1; then
    echo "seed-smoke: postgres ready after ${i}s"
    break
  fi
  if [[ $i -eq 60 ]]; then
    echo "seed-smoke: postgres did not become ready within 60s" >&2
    docker compose logs postgres >&2 || true
    exit 1
  fi
  sleep 1
done

echo "seed-smoke: waiting for spanner-emulator"
for i in $(seq 1 60); do
  if curl -sf http://localhost:9020 >/dev/null 2>&1 \
     || nc -z localhost 9010 >/dev/null 2>&1; then
    echo "seed-smoke: spanner-emulator ready after ${i}s"
    break
  fi
  if [[ $i -eq 60 ]]; then
    echo "seed-smoke: spanner-emulator did not become ready within 60s" >&2
    exit 1
  fi
  sleep 1
done

echo "seed-smoke: starting commerce-core (log=$LOG)"
: > "$LOG"
SPRING_PROFILES_ACTIVE=local \
  nohup ./gradlew :services:commerce-core:bootRun --no-daemon \
  >"$LOG" 2>&1 &
echo $! > "$PID_FILE"

echo "seed-smoke: waiting for /actuator/health"
READY=0
for i in $(seq 1 180); do
  if curl -sf http://localhost:8081/actuator/health >/dev/null 2>&1; then
    echo "seed-smoke: commerce-core ready after ${i}s"
    READY=1
    break
  fi
  sleep 1
done
if [[ $READY -ne 1 ]]; then
  echo "seed-smoke: commerce-core did not become ready in 180s" >&2
  tail -200 "$LOG" >&2 || true
  exit 1
fi

fail() { echo "seed-smoke: FAIL: $1" >&2; exit 1; }

echo "seed-smoke: assert /v1/areas returns >= 8 venues"
curl -sf http://localhost:8081/v1/areas | jq -e 'length >= 8' >/dev/null \
  || fail "/v1/areas returned <8 venues"

echo "seed-smoke: assert /v1/products returns >= 8 products"
PRODUCTS_JSON=$(curl -sS 'http://localhost:8081/v1/products?size=8') \
  || fail "/v1/products request failed"
echo "$PRODUCTS_JSON" | head -c 300
echo
echo "$PRODUCTS_JSON" | jq -e '.content | length >= 8' >/dev/null \
  || fail "/v1/products content length <8"

PID=$(echo "$PRODUCTS_JSON" | jq -r '.content[0].id')
if [[ -z "$PID" || "$PID" == "null" ]]; then
  fail "could not extract first product id"
fi
echo "seed-smoke: first product id = $PID"

FROM=$(date +%Y-%m-%d)
TO=$(date -v+30d +%Y-%m-%d 2>/dev/null || date -d '+30 days' +%Y-%m-%d)
echo "seed-smoke: assert /availability?from=$FROM&to=$TO has slots"
curl -sf "http://localhost:8081/v1/products/$PID/availability?from=$FROM&to=$TO" \
  | jq -e 'length > 0' >/dev/null \
  || fail "/availability returned 0 slots for $PID"

echo "seed-smoke: OK"
exit 0
