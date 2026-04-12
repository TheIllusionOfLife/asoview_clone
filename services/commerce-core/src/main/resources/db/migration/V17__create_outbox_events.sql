CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY,
    event_id       VARCHAR(255)  NOT NULL,
    event_type     VARCHAR(100)  NOT NULL,
    aggregate_id   VARCHAR(255)  NOT NULL,
    topic          VARCHAR(100)  NOT NULL,
    payload        BYTEA         NOT NULL,
    attempt_count  INTEGER       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at   TIMESTAMPTZ,
    failed_at      TIMESTAMPTZ
);

CREATE INDEX idx_outbox_events_unpublished
    ON outbox_events (created_at)
    WHERE published_at IS NULL AND failed_at IS NULL;
