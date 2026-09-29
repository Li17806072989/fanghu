USE fang_hu;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE fh_topic
  ADD COLUMN detail_content LONGTEXT NULL AFTER source_url;

ALTER TABLE fh_article
  ADD COLUMN detail_content LONGTEXT NULL AFTER source_url;
