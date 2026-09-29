USE fang_hu;

CREATE TABLE IF NOT EXISTS fh_supplier_membership (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  supplier_id BIGINT NOT NULL,
  source_type VARCHAR(20) NOT NULL,
  plan_name VARCHAR(120) NOT NULL,
  fee_amount DECIMAL(12,2),
  payment_status VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL,
  placement_enabled TINYINT NOT NULL DEFAULT 0,
  starts_at DATETIME,
  ends_at DATETIME,
  operator_user_id BIGINT,
  note VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_membership_supplier (supplier_id,created_at),
  INDEX idx_membership_status (status,ends_at),
  INDEX idx_membership_payment (payment_status,created_at)
);
