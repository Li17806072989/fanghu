-- One-time recovery for a failed standalone Fanghu migration.
-- Take a database backup before running this script. It only removes failed
-- history rows; successful migration history and business data are untouched.
-- After running it, restart the standalone backend so Flyway retries V1.
DELETE FROM fanghu_schema_history WHERE success = 0;
