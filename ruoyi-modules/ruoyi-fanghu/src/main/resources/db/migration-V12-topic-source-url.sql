USE fang_hu;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE fh_topic
  ADD COLUMN source_url VARCHAR(500) NULL AFTER source_desc;
