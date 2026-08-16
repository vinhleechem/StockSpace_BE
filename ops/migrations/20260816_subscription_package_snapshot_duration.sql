-- Preserve the package duration that was effective when a subscription was created.

ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS snapshot_duration_days INTEGER;

UPDATE subscriptions
SET snapshot_duration_days = GREATEST(end_date - start_date, 1)
WHERE snapshot_duration_days IS NULL;
