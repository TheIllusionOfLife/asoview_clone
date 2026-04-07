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
}

resource "google_pubsub_topic" "topics" {
  for_each = toset(local.topics)
  name     = each.value
  project  = var.project_id
}
