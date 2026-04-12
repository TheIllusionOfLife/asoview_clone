variable "project_id" { type = string }

locals {
  topics = [
    "order-events",
    "payment-events",
    "checkin-events",
    "inventory-events",
    "analytics-events",
    "product-index-events",
    "review-events",
    "favorite-events",
    "point-events",
  ]

  # Dead-letter topic for messages that fail processing after max retries.
  dlq_topic = "analytics-dlq"

  # Subscriptions consumed by analytics-ingest.
  analytics_subscriptions = {
    "order-events-analytics-sub"   = "order-events"
    "payment-events-analytics-sub" = "payment-events"
  }
}

resource "google_pubsub_topic" "topics" {
  for_each = toset(local.topics)
  name     = each.value
  project  = var.project_id
}

resource "google_pubsub_topic" "dlq" {
  name    = local.dlq_topic
  project = var.project_id
}

resource "google_pubsub_subscription" "analytics" {
  for_each = local.analytics_subscriptions
  name     = each.key
  topic    = google_pubsub_topic.topics[each.value].id
  project  = var.project_id

  ack_deadline_seconds       = 30
  message_retention_duration = "604800s" # 7 days

  dead_letter_policy {
    dead_letter_topic     = google_pubsub_topic.dlq.id
    max_delivery_attempts = 5
  }

  retry_policy {
    minimum_backoff = "10s"
    maximum_backoff = "600s"
  }
}

# Subscription for dead-letter monitoring/alerting.
resource "google_pubsub_subscription" "dlq_sub" {
  name    = "analytics-dlq-sub"
  topic   = google_pubsub_topic.dlq.id
  project = var.project_id

  ack_deadline_seconds       = 30
  message_retention_duration = "604800s"
}
