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

  # Subscriptions consumed by search-service. Separate map (not merged into
  # analytics_subscriptions) because the search lane doesn't share the
  # analytics DLQ routing — a failed reindex is recoverable from the startup
  # IndexerBackfillJob, not a back-of-queue forward.
  search_subscriptions = {
    "search-service-product-index-sub" = "product-index-events"
  }
}

variable "search_service_member" {
  type        = string
  description = "Pub/Sub subscriber principal for search-service (e.g. serviceAccount:search-service-vertex@<project>.iam.gserviceaccount.com). Empty string skips IAM wiring so local/unit terraform can still plan."
  default     = ""
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

# The Pub/Sub service agent needs publish permission on the DLQ topic
# for dead-letter forwarding to work. Without this, delivery failures
# are silently dropped instead of routed to the DLQ.
data "google_project" "current" {
  project_id = var.project_id
}

resource "google_pubsub_topic_iam_member" "dlq_publisher" {
  topic   = google_pubsub_topic.dlq.id
  role    = "roles/pubsub.publisher"
  member  = "serviceAccount:service-${data.google_project.current.number}@gcp-sa-pubsub.iam.gserviceaccount.com"
  project = var.project_id
}

# The Pub/Sub service agent also needs subscriber permission on each source
# subscription to acknowledge messages after forwarding them to the DLQ.
resource "google_pubsub_subscription_iam_member" "dlq_subscriber" {
  for_each     = local.analytics_subscriptions
  subscription = google_pubsub_subscription.analytics[each.key].id
  role         = "roles/pubsub.subscriber"
  member       = "serviceAccount:service-${data.google_project.current.number}@gcp-sa-pubsub.iam.gserviceaccount.com"
  project      = var.project_id
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

# Search-service subscription for auto-reindex. Listens on
# `product-index-events` so commerce-core's AFTER_COMMIT publish drives
# Vertex AI Search updates without operator runbook steps. Poison-pill
# messages (malformed payloads or persistently failing reindex) dead-letter
# into the shared analytics DLQ after 5 attempts so they don't infinitely
# retry. Recovery for legitimate backlog uses IndexerBackfillJob + the
# per-product admin endpoint.
resource "google_pubsub_subscription" "search" {
  for_each = local.search_subscriptions
  name     = each.key
  topic    = google_pubsub_topic.topics[each.value].id
  project  = var.project_id

  ack_deadline_seconds       = 60
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

# DLQ forwarding requires the Pub/Sub service agent to hold subscriber on
# each source subscription AND publisher on the DLQ topic. The publisher
# grant is already in `dlq_publisher` above; add the subscriber grant on
# the search subscriptions so the agent can forward dead letters.
resource "google_pubsub_subscription_iam_member" "search_dlq_subscriber" {
  for_each     = local.search_subscriptions
  subscription = google_pubsub_subscription.search[each.key].id
  role         = "roles/pubsub.subscriber"
  member       = "serviceAccount:service-${data.google_project.current.number}@gcp-sa-pubsub.iam.gserviceaccount.com"
  project      = var.project_id
}

resource "google_pubsub_subscription_iam_member" "search_subscriber" {
  for_each     = var.search_service_member == "" ? {} : local.search_subscriptions
  subscription = google_pubsub_subscription.search[each.key].id
  role         = "roles/pubsub.subscriber"
  member       = var.search_service_member
  project      = var.project_id
}

# Subscription for dead-letter monitoring/alerting.
resource "google_pubsub_subscription" "dlq_sub" {
  name    = "analytics-dlq-sub"
  topic   = google_pubsub_topic.dlq.id
  project = var.project_id

  ack_deadline_seconds       = 30
  message_retention_duration = "604800s"
}
