-- Flyway baseline for the Fanghu business schema.
-- The RuoYi system schema is provisioned by script/sql/ry_vue.sql.
CREATE TABLE IF NOT EXISTS fh_change_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  biz_type VARCHAR(40) NOT NULL,
  biz_id BIGINT NOT NULL,
  field_name VARCHAR(80) NOT NULL,
  old_value TEXT,
  new_value TEXT,
  operator_user_id BIGINT,
  operator_name VARCHAR(64),
  request_id VARCHAR(64),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_change_biz (biz_type, biz_id, created_at),
  INDEX idx_change_operator (operator_user_id, created_at)
);
