USE fang_hu;

CREATE TABLE IF NOT EXISTS fh_file_download_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  file_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  user_role VARCHAR(30) NOT NULL,
  ip_address VARCHAR(64),
  user_agent VARCHAR(500),
  result VARCHAR(20) NOT NULL,
  expires_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_file_download_file(file_id,created_at),
  INDEX idx_file_download_user(user_id,created_at)
);
