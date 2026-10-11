-- During the V8 rolling deployment the old auth instance could consume
-- requeued rows before its replacement started, failing them again.
-- Apply this follow-up only after all auth instances have the fixed producer.
-- Reuse the incident-specific filter; do not replay published events or mail.
UPDATE outbox_events
SET status = 'PENDING', attempts = 0, last_error = NULL, version = version + 1
WHERE status = 'FAILED'
  AND event_type = 'audit.event'
  AND last_error = 'Failed to construct kafka producer'
  AND published_at IS NULL
  AND created_at >= TIMESTAMPTZ '2026-10-11 15:38:00+00';
