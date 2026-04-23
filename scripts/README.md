# scripts/

One-off operator scripts for the dev cluster. They all read credentials from
the current shell; none of them hard-code secrets.

## `bootstrap-dev-secrets.sh` — populate Google Secret Manager

Writes the shell's environment values into Secret Manager in the configured
GCP project. External Secrets Operator (installed via Argo CD as the
`external-secrets` Application) syncs those values into the in-cluster
`firebase-config`, `google-ai-config`, `smtp-config`, and `stripe-test`
Secrets that the various deployment.yaml manifests reference.

### Required env vars

| Variable | Where to get it |
|---|---|
| `FIREBASE_API_KEY`, `FIREBASE_APP_ID`, `FIREBASE_AUTH_DOMAIN`, `FIREBASE_PROJECT_ID` | Firebase Console → Project Settings → SDK setup. `FIREBASE_PROJECT_ID` is always `asoview-clone-dev`. |
| `GOOGLE_API_KEY` | ai.google.dev → Get API key. Free tier is fine for dev. |
| `STRIPE_PUBLISHABLE_KEY`, `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` | Stripe Dashboard → Developers → API keys (test mode). Webhook secret comes from the webhook endpoint UI. |

### Optional env vars

| Variable | Default | Purpose |
|---|---|---|
| `PROJECT_ID` | `asoview-clone-dev` | GCP project to write to. |
| `SKIP_VALIDATION` | `0` | Set to `1` to skip the final wait on ExternalSecrets reporting `SecretSynced=True`. |

### Usage

```bash
export FIREBASE_API_KEY=...
export FIREBASE_APP_ID=...
export FIREBASE_AUTH_DOMAIN=asoview-clone-dev.firebaseapp.com
export FIREBASE_PROJECT_ID=asoview-clone-dev
export GOOGLE_API_KEY=...
export STRIPE_PUBLISHABLE_KEY=pk_test_...
export STRIPE_SECRET_KEY=sk_test_...
export STRIPE_WEBHOOK_SECRET=whsec_...

./scripts/bootstrap-dev-secrets.sh
```

The script exits 0 once every ExternalSecret reports Ready. If it times out
after 3 minutes, run `kubectl get externalsecrets -A` to inspect why.

## `provision-scanner-claim.sh` — grant a Firebase user the SCANNER role

Called by the end-to-end walkthrough so a seed test user becomes a valid
operator. Sets the `roles` + `scannerVenues` custom claims via Firebase
Admin SDK.

See the script header for env vars.

## `provision-operator-claim.sh` — grant a Firebase user operator-web access

Stamps `admin=true` + `tenantId` custom claims so the user can hit
`GET /v1/op/**` on reservation-service and see the venue dropdown on
asoview-operator. Merges with any existing custom claims (SCANNER etc.)
rather than overwriting them.

See the script header for env vars.

## `e2e-walkthrough.sh` — full ship-readiness test

Runs the complete consumer → pass → scan → operator-approval loop against
the live dev cluster and exits 0 on success. This is the merge gate for
PR 5d.

See the script header for env vars and expected seed state.
