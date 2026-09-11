USE fang_hu;

ALTER TABLE fh_lead ADD COLUMN customer_confirmation VARCHAR(20) NOT NULL DEFAULT 'PENDING' AFTER match_score;
ALTER TABLE fh_lead ADD COLUMN supplier_confirmation VARCHAR(20) NOT NULL DEFAULT 'PENDING' AFTER customer_confirmation;
ALTER TABLE fh_lead ADD COLUMN customer_confirmed_at DATETIME NULL AFTER supplier_confirmation;
ALTER TABLE fh_lead ADD COLUMN supplier_confirmed_at DATETIME NULL AFTER customer_confirmed_at;
ALTER TABLE fh_lead ADD COLUMN operator_user_id BIGINT NULL AFTER supplier_confirmed_at;
ALTER TABLE fh_lead ADD INDEX idx_lead_operator(operator_user_id);

CREATE TABLE fh_lead_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  lead_id BIGINT NOT NULL,
  action VARCHAR(40) NOT NULL,
  before_status VARCHAR(20),
  after_status VARCHAR(20),
  remark VARCHAR(500),
  actor_user_id BIGINT,
  actor_name VARCHAR(64) NOT NULL,
  actor_role VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_lead_event_lead(lead_id,created_at),
  INDEX idx_lead_event_actor(actor_user_id)
);
