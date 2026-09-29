USE fang_hu;

ALTER TABLE fh_article
  ADD COLUMN image_url VARCHAR(500) NULL AFTER summary;
