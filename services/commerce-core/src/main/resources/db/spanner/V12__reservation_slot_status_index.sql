CREATE INDEX idx_reservations_slot_status_created ON reservations(slot_id, status, created_at);
