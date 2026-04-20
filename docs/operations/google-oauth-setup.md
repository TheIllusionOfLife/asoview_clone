# Google Sign-In Setup (one-time, manual)

Status: the Terraform module at `infra/terraform/modules/identity-platform/` registers the Google provider and ships the real `client_id` via variable. The matching `client_secret` is set in GCP Console after `terraform apply`. The `NEXT_PUBLIC_ENABLE_GOOGLE_SIGNIN` build-time flag (Cloud Build substitution `_ENABLE_GOOGLE_SIGNIN`) controls whether the button renders; flip to `true` once sign-in is verified.

## Why this is manual

Two reasons the flow cannot be end-to-end Terraformed:

1. **OAuth consent screen + client provisioning**: creating the OAuth 2.0 Web Client and publishing the consent screen (audience, test users, scopes) requires interactive GCP Console review. No Terraform resource covers that API surface.
2. **The client_secret must never land in Terraform state**. Any Terraform path that reads or assigns the secret — a `data.google_secret_manager_secret_version`, a `sensitive` input variable, a `-var` at apply time — materializes plaintext into state on every plan/apply. The state backend today is local/unencrypted. The resource is therefore created with a dummy `MANAGED_OUTSIDE_TERRAFORM` placeholder and `lifecycle.ignore_changes = [client_secret]` keeps Terraform from overwriting whatever the operator later pastes via Console.

## Steps

1. **Create an OAuth 2.0 Web Client** in the `asoview-clone-dev` project.
   - GCP Console → APIs & Services → Credentials → Create credentials → OAuth client ID.
   - Application type: Web application.
   - Authorized JavaScript origin: `https://asoview-clone-dev.duckdns.org`.
   - Authorized redirect URI: `https://asoview-clone-dev.firebaseapp.com/__/auth/handler` (Identity Platform's default handler for the dev project).

2. **Publish the OAuth consent screen**.
   - User type: Internal (org-scoped) if the project sits under a Google Workspace org. External + "Testing" status otherwise — study users must be added as test users.
   - Required scopes: `openid`, `email`, `profile`. Nothing else.

3. **Pre-provision the Identity Platform service agent**.
   - First `terraform apply` of Identity Platform resources fails if the service agent hasn't been created yet. Run once per project:
     ```sh
     gcloud beta services identity create \
       --service=identitytoolkit.googleapis.com \
       --project=<project-id>
     ```

4. **Wire the client_id into Terraform**.
   - Set `google_oauth_client_id` in `infra/terraform/environments/<env>/terraform.tfvars` (the client ID is not a secret — safe to commit to a gitignored tfvars or pass via `TF_VAR_google_oauth_client_id`).
   - Run `terraform apply` in `infra/terraform/environments/<env>/`. The Identity Platform config is created with `client_id = <real>` and `client_secret = "MANAGED_OUTSIDE_TERRAFORM"`.

5. **Paste the real client_secret via GCP Console**.
   - GCP Console → Identity Platform → Providers → Google → Edit.
   - Paste the OAuth client secret generated in step 1.
   - Save. Sign-in starts working immediately.
   - Subsequent `terraform apply` runs will NOT overwrite the value because of `lifecycle.ignore_changes`.

   Operator convenience: if you want a backup copy of the secret, store it in Secret Manager manually — it is not referenced by Terraform.
   ```sh
   echo -n "<client-secret>" | gcloud secrets create identity-platform-google-client-secret \
     --project=<project-id> --data-file=-
   ```

6. **Enable the frontend button**.
   - In the Cloud Build trigger for `web-deploy`, set substitution `_ENABLE_GOOGLE_SIGNIN=true`.
   - Re-run the trigger. The rebuilt image has `NEXT_PUBLIC_ENABLE_GOOGLE_SIGNIN=true` baked in.
   - Argo CD rolls out the new image within its sync interval.

7. **Verify**.
   - `curl -s https://asoview-clone-dev.duckdns.org/ja/signin | grep -i "Continue with Google"` matches.
   - Click the button in a browser → OAuth popup → consent → returns signed in.
   - Console shows no `auth/internal-error`.

## Rotation

The OAuth client secret rotates via the same Console path as step 5. Terraform remains ignorant of the value. If you rotate: (a) generate a new secret in GCP Console → Credentials, (b) paste it into Identity Platform → Google provider → Edit, (c) optionally update the Secret Manager backup. No `terraform apply` needed.

## Rollback

Set substitution `_ENABLE_GOOGLE_SIGNIN=false` on the Cloud Build trigger and re-run. The rebuilt image hides the button; email/password sign-in keeps working. No infra changes.
