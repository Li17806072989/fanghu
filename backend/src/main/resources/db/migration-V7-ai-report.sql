USE fang_hu;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='fang_hu' AND table_name='fh_ai_job' AND column_name='access_status') = 0,
  'ALTER TABLE fh_ai_job ADD COLUMN access_status VARCHAR(20) NOT NULL DEFAULT ''NOT_REQUESTED'' AFTER result_file_id, ADD COLUMN access_reason VARCHAR(500) AFTER access_status, ADD COLUMN decided_by BIGINT AFTER access_reason, ADD COLUMN decided_at DATETIME AFTER decided_by, ADD COLUMN generator VARCHAR(80) AFTER decided_at, ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at, ADD INDEX idx_ai_job_requester(requester_id,created_at), ADD INDEX idx_ai_job_access(access_status,created_at), ADD INDEX idx_ai_job_status(status,created_at)',
  'SELECT 1'
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

CREATE TABLE IF NOT EXISTS fh_ai_report_content (
  job_id BIGINT PRIMARY KEY,
  content LONGTEXT NOT NULL,
  generator VARCHAR(80) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
