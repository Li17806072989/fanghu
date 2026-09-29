-- Add logical deletion to all mutable Fanghu aggregates.
ALTER TABLE fh_topic ADD COLUMN del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0正常 1删除';
ALTER TABLE fh_article ADD COLUMN del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0正常 1删除';
ALTER TABLE fh_topic_category ADD COLUMN del_flag CHAR(1) NOT NULL DEFAULT '0';
ALTER TABLE fh_taxonomy_node ADD COLUMN del_flag CHAR(1) NOT NULL DEFAULT '0';
ALTER TABLE fh_content_taxonomy ADD COLUMN del_flag CHAR(1) NOT NULL DEFAULT '0';
ALTER TABLE fh_supplier_company ADD COLUMN del_flag CHAR(1) NOT NULL DEFAULT '0';
ALTER TABLE fh_supplier_membership ADD COLUMN del_flag CHAR(1) NOT NULL DEFAULT '0';
ALTER TABLE fh_solution ADD COLUMN del_flag CHAR(1) NOT NULL DEFAULT '0';
ALTER TABLE fh_customer_profile ADD COLUMN del_flag CHAR(1) NOT NULL DEFAULT '0';
ALTER TABLE fh_demand ADD COLUMN del_flag CHAR(1) NOT NULL DEFAULT '0';
ALTER TABLE fh_lead ADD COLUMN del_flag CHAR(1) NOT NULL DEFAULT '0';
ALTER TABLE fh_lead_event ADD COLUMN del_flag CHAR(1) NOT NULL DEFAULT '0';
ALTER TABLE fh_file_asset ADD COLUMN del_flag CHAR(1) NOT NULL DEFAULT '0';
ALTER TABLE fh_file_download_log ADD COLUMN del_flag CHAR(1) NOT NULL DEFAULT '0';
ALTER TABLE fh_ad_slot ADD COLUMN del_flag CHAR(1) NOT NULL DEFAULT '0';
ALTER TABLE fh_platform_contact ADD COLUMN del_flag CHAR(1) NOT NULL DEFAULT '0';
ALTER TABLE fh_ai_job ADD COLUMN del_flag CHAR(1) NOT NULL DEFAULT '0';
ALTER TABLE fh_audit_record ADD COLUMN del_flag CHAR(1) NOT NULL DEFAULT '0';

-- Query paths used by portal search, workbenches and audit queues.
CREATE INDEX idx_topic_active_search ON fh_topic (status, del_flag, published_at, category_id);
CREATE INDEX idx_article_active_search ON fh_article (status, del_flag, published_at, category_id);
CREATE INDEX idx_solution_active_search ON fh_solution (audit_status, del_flag, category_id, supplier_id);
CREATE INDEX idx_demand_active_owner ON fh_demand (customer_id, del_flag, status, created_at);
CREATE INDEX idx_lead_active_parties ON fh_lead (customer_id, supplier_id, del_flag, status, updated_at);
CREATE INDEX idx_supplier_active_owner ON fh_supplier_company (owner_user_id, del_flag, verification_status);

-- Idempotency and business uniqueness constraints.
CREATE UNIQUE INDEX uk_supplier_membership_active ON fh_supplier_membership (supplier_id, plan_name, starts_at, del_flag);
CREATE UNIQUE INDEX uk_ai_job_request_title ON fh_ai_job (requester_id, title, created_at, del_flag);

-- Database-level audit for sensitive state and identity fields. The actor is
-- supplied by the application audit log when available; triggers guarantee
-- that direct SQL changes are still visible.
CREATE TRIGGER trg_fh_topic_change AFTER UPDATE ON fh_topic
FOR EACH ROW BEGIN
  IF NOT (OLD.status <=> NEW.status) THEN
    INSERT INTO fh_change_log(biz_type,biz_id,field_name,old_value,new_value)
      VALUES ('TOPIC',NEW.id,'status',OLD.status,NEW.status);
  END IF;
  IF NOT (OLD.title <=> NEW.title) THEN
    INSERT INTO fh_change_log(biz_type,biz_id,field_name,old_value,new_value)
      VALUES ('TOPIC',NEW.id,'title',OLD.title,NEW.title);
  END IF;
END;
CREATE TRIGGER trg_fh_article_change AFTER UPDATE ON fh_article
FOR EACH ROW BEGIN
  IF NOT (OLD.status <=> NEW.status) THEN
    INSERT INTO fh_change_log(biz_type,biz_id,field_name,old_value,new_value)
      VALUES ('ARTICLE',NEW.id,'status',OLD.status,NEW.status);
  END IF;
END;
CREATE TRIGGER trg_fh_solution_change AFTER UPDATE ON fh_solution
FOR EACH ROW BEGIN
  IF NOT (OLD.audit_status <=> NEW.audit_status) THEN
    INSERT INTO fh_change_log(biz_type,biz_id,field_name,old_value,new_value)
      VALUES ('SOLUTION',NEW.id,'audit_status',OLD.audit_status,NEW.audit_status);
  END IF;
END;
CREATE TRIGGER trg_fh_supplier_change AFTER UPDATE ON fh_supplier_company
FOR EACH ROW BEGIN
  IF NOT (OLD.verification_status <=> NEW.verification_status) THEN
    INSERT INTO fh_change_log(biz_type,biz_id,field_name,old_value,new_value)
      VALUES ('SUPPLIER',NEW.id,'verification_status',OLD.verification_status,NEW.verification_status);
  END IF;
END;
CREATE TRIGGER trg_fh_demand_change AFTER UPDATE ON fh_demand
FOR EACH ROW BEGIN
  IF NOT (OLD.status <=> NEW.status) THEN
    INSERT INTO fh_change_log(biz_type,biz_id,field_name,old_value,new_value)
      VALUES ('DEMAND',NEW.id,'status',OLD.status,NEW.status);
  END IF;
END;
CREATE TRIGGER trg_fh_lead_change AFTER UPDATE ON fh_lead
FOR EACH ROW BEGIN
  IF NOT (OLD.status <=> NEW.status) THEN
    INSERT INTO fh_change_log(biz_type,biz_id,field_name,old_value,new_value)
      VALUES ('LEAD',NEW.id,'status',OLD.status,NEW.status);
  END IF;
END;
