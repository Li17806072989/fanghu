-- Fanghu business schema for a fresh database.
-- V1 creates the audit table; V2 adds soft-delete/index/trigger hardening.
-- This migration is deliberately between V1 and V2 so existing V2 databases
-- remain untouched while empty databases can be provisioned end to end.

CREATE TABLE IF NOT EXISTS fh_topic_category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, parent_id BIGINT NOT NULL DEFAULT 0,
  name VARCHAR(80) NOT NULL, sort_no INT NOT NULL DEFAULT 0,
  enabled TINYINT NOT NULL DEFAULT 1, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_topic_category(parent_id,name)
);
CREATE TABLE IF NOT EXISTS fh_article_category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(80) NOT NULL,
  sort_no INT NOT NULL DEFAULT 0, enabled TINYINT NOT NULL DEFAULT 1,
  UNIQUE KEY uk_article_category_name(name)
);
CREATE TABLE IF NOT EXISTS fh_taxonomy_node (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, parent_id BIGINT NOT NULL DEFAULT 0,
  taxonomy_type VARCHAR(20) NOT NULL, code VARCHAR(60) NOT NULL,
  name VARCHAR(80) NOT NULL, description VARCHAR(255), sort_no INT NOT NULL DEFAULT 0,
  enabled TINYINT NOT NULL DEFAULT 1, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_taxonomy_type_code(taxonomy_type,code),
  UNIQUE KEY uk_taxonomy_parent_name(taxonomy_type,parent_id,name)
);
CREATE TABLE IF NOT EXISTS fh_topic (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, category_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL, summary VARCHAR(500), cover_url VARCHAR(500),
  source_desc VARCHAR(255), source_url VARCHAR(500), detail_content LONGTEXT,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', is_recommended TINYINT NOT NULL DEFAULT 0,
  published_at DATETIME, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_topic_category(category_id), INDEX idx_topic_status(status)
);
CREATE TABLE IF NOT EXISTS fh_article (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, category_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL, summary VARCHAR(500), image_url VARCHAR(500),
  content LONGTEXT NOT NULL, source_name VARCHAR(120), source_url VARCHAR(500),
  detail_content LONGTEXT, status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  is_hot TINYINT NOT NULL DEFAULT 0, published_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_article_category(category_id), INDEX idx_article_status(status,published_at)
);
CREATE TABLE IF NOT EXISTS fh_content_taxonomy (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, content_type VARCHAR(20) NOT NULL,
  content_id BIGINT NOT NULL, taxonomy_id BIGINT NOT NULL, primary_flag TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_content_taxonomy(content_type,content_id,taxonomy_id),
  INDEX idx_content_taxonomy_node(taxonomy_id,content_type,content_id)
);
CREATE TABLE IF NOT EXISTS fh_supplier_company (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, owner_user_id BIGINT NOT NULL,
  name VARCHAR(200) NOT NULL, credit_code VARCHAR(40), intro TEXT,
  verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  service_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', rating_score DECIMAL(5,2) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_supplier_owner(owner_user_id), UNIQUE KEY uk_supplier_credit(credit_code)
);
CREATE TABLE IF NOT EXISTS fh_supplier_membership (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, supplier_id BIGINT NOT NULL,
  source_type VARCHAR(20) NOT NULL, plan_name VARCHAR(120) NOT NULL,
  fee_amount DECIMAL(12,2), payment_status VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL,
  placement_enabled TINYINT NOT NULL DEFAULT 0, starts_at DATETIME, ends_at DATETIME,
  operator_user_id BIGINT, note VARCHAR(500), created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_membership_supplier(supplier_id,created_at)
);
CREATE TABLE IF NOT EXISTS fh_solution (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, supplier_id BIGINT NOT NULL, title VARCHAR(200) NOT NULL,
  category_id BIGINT NOT NULL, scene_tags VARCHAR(500), summary VARCHAR(500), content LONGTEXT,
  audit_status VARCHAR(20) NOT NULL DEFAULT 'PENDING', visibility VARCHAR(20) NOT NULL DEFAULT 'AUTHORIZED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_solution_category(category_id), INDEX idx_solution_supplier(supplier_id)
);
CREATE TABLE IF NOT EXISTS fh_customer_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, organization VARCHAR(200),
  contact_name VARCHAR(80) NOT NULL, contact_phone VARCHAR(30),
  verification_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_customer_user(user_id)
);
CREATE TABLE IF NOT EXISTS fh_demand (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, customer_id BIGINT NOT NULL, title VARCHAR(200) NOT NULL,
  description TEXT NOT NULL, category_id BIGINT, budget_level VARCHAR(30),
  status VARCHAR(20) NOT NULL DEFAULT 'NEW', created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_demand_customer(customer_id)
);
CREATE TABLE IF NOT EXISTS fh_lead (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, demand_id BIGINT NOT NULL, customer_id BIGINT NOT NULL,
  supplier_id BIGINT NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'NEW', match_score DECIMAL(5,2),
  customer_confirmation VARCHAR(20) NOT NULL DEFAULT 'PENDING', supplier_confirmation VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  customer_confirmed_at DATETIME, supplier_confirmed_at DATETIME, operator_user_id BIGINT,
  remark VARCHAR(500), next_action_at DATETIME, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_demand_supplier(demand_id,supplier_id), INDEX idx_lead_status(status)
);
CREATE TABLE IF NOT EXISTS fh_lead_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, lead_id BIGINT NOT NULL, action VARCHAR(40) NOT NULL,
  before_status VARCHAR(20), after_status VARCHAR(20), remark VARCHAR(500), actor_user_id BIGINT,
  actor_name VARCHAR(64) NOT NULL, actor_role VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_lead_event_lead(lead_id,created_at)
);
CREATE TABLE IF NOT EXISTS fh_file_asset (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, owner_type VARCHAR(30) NOT NULL, owner_id BIGINT NOT NULL,
  object_key VARCHAR(500) NOT NULL, sha256 CHAR(64), visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
  scan_status VARCHAR(20) NOT NULL DEFAULT 'PENDING', download_count INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_file_owner(owner_type,owner_id)
);
CREATE TABLE IF NOT EXISTS fh_file_download_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, file_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
  user_role VARCHAR(30) NOT NULL, ip_address VARCHAR(64), user_agent VARCHAR(500),
  result VARCHAR(20) NOT NULL, expires_at DATETIME, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_file_download_file(file_id,created_at)
);
CREATE TABLE IF NOT EXISTS fh_ad_slot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, slot_code VARCHAR(60) NOT NULL, title VARCHAR(120) NOT NULL,
  image_url VARCHAR(500), target_url VARCHAR(500), start_at DATETIME, end_at DATETIME,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', sort_no INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ad_slot(slot_code)
);
CREATE TABLE IF NOT EXISTS fh_platform_contact (
  id BIGINT PRIMARY KEY, hotline VARCHAR(40), email VARCHAR(120), address VARCHAR(255),
  service_hours VARCHAR(120), created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS fh_supplier_metric (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, supplier_id BIGINT NOT NULL, metric_date DATE NOT NULL,
  impressions INT NOT NULL DEFAULT 0, views INT NOT NULL DEFAULT 0, inquiries INT NOT NULL DEFAULT 0,
  downloads INT NOT NULL DEFAULT 0, qualified_leads INT NOT NULL DEFAULT 0, won_leads INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_supplier_metric(supplier_id,metric_date)
);
CREATE TABLE IF NOT EXISTS fh_ai_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, requester_id BIGINT NOT NULL, title VARCHAR(200) NOT NULL,
  source_ids JSON, status VARCHAR(20) NOT NULL DEFAULT 'QUEUED', toc_json JSON, result_file_id BIGINT,
  access_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUESTED', access_reason VARCHAR(500), decided_by BIGINT,
  decided_at DATETIME, generator VARCHAR(80), expires_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_ai_job_requester(requester_id,created_at)
);
CREATE TABLE IF NOT EXISTS fh_ai_report_content (
  job_id BIGINT PRIMARY KEY, content LONGTEXT NOT NULL, generator VARCHAR(80) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS fh_audit_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, biz_type VARCHAR(20) NOT NULL, biz_id BIGINT NOT NULL,
  action VARCHAR(20) NOT NULL, before_status VARCHAR(20) NOT NULL, after_status VARCHAR(20) NOT NULL,
  opinion VARCHAR(1000), operator_user_id BIGINT NOT NULL, operator_name VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_audit_biz(biz_type,biz_id), INDEX idx_audit_created(created_at)
);

INSERT INTO fh_article_category(id,name,sort_no,enabled) VALUES
 (1,'政策与行业动态',10,1),(2,'装备技术',20,1),(3,'市场与采购',30,1),(4,'任务应用',40,1)
ON DUPLICATE KEY UPDATE name=VALUES(name),sort_no=VALUES(sort_no),enabled=VALUES(enabled);
INSERT INTO fh_topic_category(parent_id,name,sort_no,enabled) VALUES
 (0,'车辆与机动平台',10,1),(0,'通信与指挥系统',20,1),(0,'无人系统',30,1),
 (0,'侦察与感知设备',40,1),(0,'防护与保障装备',50,1),(0,'训练与仿真系统',60,1)
 ON DUPLICATE KEY UPDATE sort_no=VALUES(sort_no),enabled=VALUES(enabled);
INSERT INTO fh_taxonomy_node(parent_id,taxonomy_type,code,name,sort_no,enabled) VALUES
 (0,'EQUIPMENT','vehicle','车辆与机动平台',10,1),
 (0,'EQUIPMENT','command','通信与指挥系统',20,1),
 (0,'EQUIPMENT','unmanned','无人系统',30,1),
 (0,'EQUIPMENT','sensing','侦察与感知设备',40,1),
 (0,'EQUIPMENT','protection','防护与保障装备',50,1),
 (0,'EQUIPMENT','training','训练与仿真系统',60,1)
 ON DUPLICATE KEY UPDATE name=VALUES(name),sort_no=VALUES(sort_no),enabled=VALUES(enabled);
INSERT INTO fh_platform_contact(id) VALUES (1) ON DUPLICATE KEY UPDATE id=id;
INSERT INTO fh_ad_slot(slot_code,title,target_url,status,sort_no)
 VALUES ('HOME_SUPPLIER_ENTRY','供应商能力入驻与方案发布','/supplier/apply','ACTIVE',10)
 ON DUPLICATE KEY UPDATE title=VALUES(title),target_url=VALUES(target_url),status=VALUES(status);

-- Referential integrity for business ownership and workflow records.
ALTER TABLE fh_supplier_membership ADD CONSTRAINT fk_membership_supplier FOREIGN KEY (supplier_id) REFERENCES fh_supplier_company(id);
ALTER TABLE fh_solution ADD CONSTRAINT fk_solution_supplier FOREIGN KEY (supplier_id) REFERENCES fh_supplier_company(id);
ALTER TABLE fh_lead ADD CONSTRAINT fk_lead_demand FOREIGN KEY (demand_id) REFERENCES fh_demand(id);
ALTER TABLE fh_lead ADD CONSTRAINT fk_lead_supplier FOREIGN KEY (supplier_id) REFERENCES fh_supplier_company(id);
ALTER TABLE fh_lead_event ADD CONSTRAINT fk_lead_event_lead FOREIGN KEY (lead_id) REFERENCES fh_lead(id);
ALTER TABLE fh_file_download_log ADD CONSTRAINT fk_download_file FOREIGN KEY (file_id) REFERENCES fh_file_asset(id);
ALTER TABLE fh_ai_report_content ADD CONSTRAINT fk_ai_content_job FOREIGN KEY (job_id) REFERENCES fh_ai_job(id);
