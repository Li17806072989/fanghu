USE fang_hu;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='fang_hu' AND table_name='fh_topic' AND column_name='is_recommended') = 0,
  'ALTER TABLE fh_topic ADD COLUMN is_recommended TINYINT NOT NULL DEFAULT 0 AFTER status, ADD INDEX idx_topic_recommended(status,is_recommended,published_at)',
  'SELECT 1'
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='fang_hu' AND table_name='fh_ad_slot' AND column_name='created_at') = 0,
  'ALTER TABLE fh_ad_slot ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP',
  'SELECT 1'
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

CREATE TABLE IF NOT EXISTS fh_platform_contact (
  id BIGINT PRIMARY KEY,
  hotline VARCHAR(40),
  email VARCHAR(120),
  address VARCHAR(255),
  service_hours VARCHAR(120),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO fh_platform_contact(id,hotline,email,address,service_hours)
VALUES(1,NULL,NULL,NULL,NULL)
ON DUPLICATE KEY UPDATE id=VALUES(id);

INSERT INTO fh_ad_slot(slot_code,title,image_url,target_url,start_at,end_at,status,sort_no)
VALUES('HOME_SUPPLIER_ENTRY','供应商能力入驻与方案发布',NULL,'/supplier/apply',NULL,NULL,'ACTIVE',10)
ON DUPLICATE KEY UPDATE target_url=VALUES(target_url);
