# Google Sign-In Setup (one-time, manual)

Status: pending. Google sign-in on `apps/asoview-web` is gated out until this runbook is completed. The button is hidden behind `NEXT_PUBLIC_ENABLE_GOOGLE_SIGNIN`; leaving the flag unset is the fail-closed default.

## Why this is manual

The Terraform module at `infra/terraform/modules/identity-platform/main.tf` carries placeholder strings for the Google provider's OAuth client:

```hcl
resource "google_identity_platform_default_supported_idp_config" "google" {
  client_id     = "PLACEHOLDER_OAUTH_CLIENT_ID"
  client_secret = "PLACEHOLDER_OAUTH_CLIENT_SECRET"
  enabled       = true
}
```

Identity Platform forwards these to Google at sign-in time. A real OAuth 2.0 Web Client has to be provisioned through the GCP Console because:

1. The OAuth consent screen requires interactive review (publisher, scopes, test users) that Terraform cannot automate.
2. The client secret should land in Secret Manager, not in a committed `.tf` file.

## Steps

1. **Create an OAuth 2.0 Web Client** in the `asoview-clone-dev` project.
   - GCP Console → APIs & Services → Credentials → Create credentials → OAuth client ID.
   - Application type: Web application.
   - Authorized JavaScript origin: `https://asoview-clone-dev.duckdns.org`.
   - Authorized redirect URI: `https://asoview-clone-dev.firebaseapp.com/__/auth/handler` (Identity Platform's default handler for the dev project).

2. **Publish the OAuth consent screen**.
   - User type: Internal (org-scoped) if the project sits under a Google Workspace org. External + "Testing" status otherwise — study users must be added as test users.
   - Required scopes: `openid`, `email`, `profile`. Nothing else.

3. **Store the client secret in Secret Manager**.
   ```sh
   echo -n "<client-secret>" | gcloud secrets create identity-platform-google-client-secret \
     --project=asoview-clone-dev --data-file=-
   ```
   The client ID is not a secret and can be wired via a Terraform variable directly.

4. **Wire the real values into Terraform**.
   - Set `google_oauth_client_id` and (optionally) `google_oauth_client_secret_id` in
     `infra/terraform/environments/<env>/terraform.tfvars`. The secret ID defaults
     to `projects/<project-number>/secrets/identity-platform-google-client-secret`.
   - **Pre-provision the Identity Platform service agent** once per project — the
     first `terraform apply` of the new IAM binding fails if the agent hasn't been
     created yet:
     ```sh
     gcloud beta services identity create \
       --service=identitytoolkit.googleapis.com \
       --project=<project-id>
     ```
   - `terraform apply` in `infra/terraform/environments/<env>/`.

5. **Enable the frontend button**.
   - In `infra/k8s/web/overlays/dev/kustomization.yaml`, add
     `NEXT_PUBLIC_ENABLE_GOOGLE_SIGNIN=true` to the frontend env.
   - Update the E2E assertion in `apps/asoview-web/e2e/smoke/live.spec.ts` from "button has count 0" to "button is visible" in the same commit that flips the flag.

6. **Verify**.
   - `curl https://asoview-clone-dev.duckdns.org/ja/signin` now shows the "Continue with Google" button.
   - Click it in a browser → OAuth popup → consent → returns signed in.
   - Console shows no `auth/internal-error`.

## Rollback

Set `NEXT_PUBLIC_ENABLE_GOOGLE_SIGNIN=false` in the overlay and redeploy. The button disappears from the render tree; no data is lost. Email/password sign-in keeps working either way.
