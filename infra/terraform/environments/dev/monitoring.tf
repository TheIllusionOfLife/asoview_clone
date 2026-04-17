# Cloud Billing budget + notification for Vertex AI Search cost overruns.
#
# Discovery Engine has a 10k-query/month free tier. The $30/month budget
# below is a safety cap, not a usage target. Threshold rules fire at
# 50%/80%/100% of that budget; alerts land in the owner's email via
# a notification channel wired to Cloud Billing.
#
# This is a billing budget, not a Cloud Monitoring metric alert, because
# Discovery Engine's per-service Cloud Monitoring usage metrics are not
# a stable public API — the budget API is the supported way to guardrail
# managed-service cost.

data "google_billing_account" "default" {
  billing_account = "00507D-A4D888-8B0E53"
}

resource "google_monitoring_notification_channel" "email_owner" {
  display_name = "Owner email (budget alerts)"
  type         = "email"
  labels = {
    email_address = "mukaiyuya@gmail.com"
  }
}

resource "google_billing_budget" "discoveryengine_guardrail" {
  billing_account = data.google_billing_account.default.id
  display_name    = "Vertex AI Search (Discovery Engine) guardrail"

  budget_filter {
    projects = ["projects/${data.google_project.current.number}"]
    # Filter to Discovery Engine service so the budget tracks only
    # Vertex AI Search cost, not the rest of the project bill.
    services = ["services/74B1-77CF-C302"] # Vertex AI Search (discoveryengine.googleapis.com)
  }

  amount {
    specified_amount {
      currency_code = "USD"
      units         = "30"
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
    disable_default_iam_recipients = true
  }
}

data "google_project" "current" {
  project_id = var.project_id
}
