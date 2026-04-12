CREATE TABLE outbox_events (
    id            UUID PRIMARY KEY,
    event_type    VARCHAR(100)  NOT NULL,
    aggregate_id  VARCHAR(255)  NOT NULL,
    topic         VARCHAR(100)  NOT NULL,
    payload       BYTEA         NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at  TIMESTAMP
);

CREATE INDEX idx_outbox_events_unpublished
    ON outbox_events (created_at)
    WHERE published_at IS NULL;
