CREATE TABLE payment_confirmation_steps (
  step_id STRING(36) NOT NULL,
  payment_id STRING(36) NOT NULL,
  order_item_id STRING(36) NOT NULL,
  hold_id STRING(36) NOT NULL,
  slot_id STRING(36) NOT NULL,
  quantity INT64 NOT NULL,
  status STRING(16) NOT NULL,
  attempted_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
  updated_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)
) PRIMARY KEY (step_id);

CREATE INDEX idx_steps_payment ON payment_confirmation_steps(payment_id);
CREATE INDEX idx_steps_status ON payment_confirmation_steps(status, attempted_at);
