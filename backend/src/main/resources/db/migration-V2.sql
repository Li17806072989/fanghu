USE fang_hu;
ALTER TABLE fh_supplier_company ADD COLUMN rating_score DECIMAL(5,2) NOT NULL DEFAULT 0 AFTER service_status;
ALTER TABLE fh_lead ADD COLUMN match_score DECIMAL(5,2) NULL AFTER status;
ALTER TABLE fh_lead ADD UNIQUE KEY uk_demand_supplier(demand_id,supplier_id);
