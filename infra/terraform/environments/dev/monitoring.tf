# Cloud Billing budget + notification for Vertex AI Search cost overruns.
#
# Discovery Engine's search tier is billed per-query; the ¥5,000/month
# (~$33 USD) budget below is a safety cap, not a usage target. Threshold
# rules fire at 50%/80%/100% of that budget; alerts land in the owner's
# email via a notification channel wired to Cloud Billing. Currency must
# match the billing account's currency (JPY) or CreateBudget returns a
# generic 400 INVALID_ARGUMENT.
#
# This is a billing budget, not a Cloud Monitoring metric alert, because
# Discovery Engine's per-service Cloud Monitoring usage metrics are not
# a stable public API — the budget API is the supported way to guardrail
# managed-service cost.

# API enablement. The billing + budgets APIs are required for the
# `google_billing_account` data source and `google_billing_budget` resource
# below; the monitoring API is required for the notification channel.
# Discovery Engine is enabled in vertex-search.tf alongside the data store
# that depends on it.
resource "google_project_service" "cloud_billing" {
  project            = var.project_id
  service            = "cloudbilling.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "billing_budgets" {
  project            = var.project_id
  service            = "billingbudgets.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "monitoring" {
  project            = var.project_id
  service            = "monitoring.googleapis.com"
  disable_on_destroy = false
}

data "google_billing_account" "default" {
  billing_account = var.billing_account_id

  depends_on = [google_project_service.cloud_billing]
}

resource "google_monitoring_notification_channel" "email_owner" {
  display_name = "Owner email (budget alerts)"
  type         = "email"
  labels = {
    email_address = var.notification_email
  }

  depends_on = [google_project_service.monitoring]
}

resource "google_billing_budget" "discoveryengine_guardrail" {
  billing_account = data.google_billing_account.default.id
  display_name    = "Vertex AI Search (Discovery Engine) guardrail"

  budget_filter {
    # Budgets API requires the project string ID (projects/asoview-clone-dev),
    # not the numeric project number. Using the number yields a generic
    # 400 INVALID_ARGUMENT with no field-level detail.
    projects = ["projects/${var.project_id}"]
    # Filter to Discovery Engine service so the budget tracks only
    # Vertex AI Search cost, not the rest of the project bill.
    services = ["services/74B1-77CF-C302"] # Vertex AI Search (discoveryengine.googleapis.com)
    # When a `services` filter is set, Cloud Billing requires
    # `credit_types_treatment` to be `EXCLUDE_ALL_CREDITS` (the
    # Terraform provider default of `INCLUDE_ALL_CREDITS` yields a
    # generic 400 INVALID_ARGUMENT at create time).
    credit_types_treatment = "EXCLUDE_ALL_CREDITS"
  }

  amount {
    specified_amount {
      # Must match the billing account's currency (asoview-clone-dev is
      # on a JPY billing account). Budget in JPY roughly equivalent to
      # $30 USD at 2026 rates; used only as a safety cap.
      currency_code = "JPY"
      units         = "5000"
    }
  }

  threshold_rules {
    threshold_percent = 0.5
    spend_basis       = "CURRENT_SPEND"
  }

  threshold_rules {
    threshold_percent = 0.8
    spend_basis       = "CURRENT_SPEND"
  }

  threshold_rules {
    threshold_percent = 1.0
    spend_basis       = "CURRENT_SPEND"
  }

  all_updates_rule {
    monitoring_notification_channels = [
      google_monitoring_notification_channel.email_owner.id,
    ]
    # Keep default IAM recipients (project owners) as a fallback so the
    # guardrail still fires if the single email channel misconfigures
    # (typo, bounced mail, user leaves). The email channel is the
    # primary path; project owners are the safety net.
    disable_default_iam_recipients = false
  }

  depends_on = [google_project_service.billing_budgets]
}
