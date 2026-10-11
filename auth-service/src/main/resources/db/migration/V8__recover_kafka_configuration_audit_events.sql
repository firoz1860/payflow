-- The Kafka client upgrade changed its default linger time. The auth producer
-- failed construction until the request/delivery budgets were corrected.
-- Retry only unpublished audit events from this incident, retaining event IDs.
-- Expired verification/reset notifications and unrelated failures stay untouched.
UPDATE outbox_events
SET status = 'PENDING', attempts = 0, last_error = NULL, version = version + 1
WHERE status = 'FAILED'
  AND event_type = 'audit.event'
  AND last_error = 'Failed to construct kafka producer'
  AND published_at IS NULL
  AND created_at >= TIMESTAMPTZ '2026-10-11 15:38:00+00';
