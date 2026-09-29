-- Local payment simulation: plans, orders, provider transactions and refunds.
CREATE TABLE IF NOT EXISTS fh_product_plan (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(60) NOT NULL,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(500),
  price DECIMAL(12,2) NOT NULL,
  currency VARCHAR(10) NOT NULL DEFAULT 'CNY',
  duration_months INT NOT NULL DEFAULT 1,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0',
  UNIQUE KEY uk_product_plan_code(code),
  INDEX idx_product_plan_enabled(enabled)
);

CREATE TABLE IF NOT EXISTS fh_payment_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(40) NOT NULL,
  user_id BIGINT NOT NULL,
  supplier_id BIGINT NOT NULL,
  plan_id BIGINT NOT NULL,
  channel VARCHAR(20) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  currency VARCHAR(10) NOT NULL DEFAULT 'CNY',
  status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
  client_request_id VARCHAR(80),
  provider_transaction_no VARCHAR(100),
  paid_at DATETIME,
  expired_at DATETIME,
  closed_at DATETIME,
  refund_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0',
  UNIQUE KEY uk_payment_order_no(order_no),
  UNIQUE KEY uk_payment_client_request(user_id,client_request_id),
  INDEX idx_payment_order_supplier(supplier_id,created_at),
  INDEX idx_payment_order_status(status,created_at)
);

CREATE TABLE IF NOT EXISTS fh_payment_transaction (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  channel VARCHAR(20) NOT NULL,
  provider_transaction_no VARCHAR(100),
  event_id VARCHAR(100) NOT NULL,
  event_type VARCHAR(30) NOT NULL,
  status VARCHAR(20) NOT NULL,
  payload TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0',
  UNIQUE KEY uk_payment_event(channel,event_id),
  INDEX idx_payment_txn_order(order_id,created_at)
);

CREATE TABLE IF NOT EXISTS fh_payment_refund (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  refund_no VARCHAR(40) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
  reason VARCHAR(500),
  provider_refund_no VARCHAR(100),
  created_by BIGINT NOT NULL,
  completed_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0',
  UNIQUE KEY uk_payment_refund_no(refund_no),
  INDEX idx_payment_refund_order(order_id,created_at)
);

INSERT INTO fh_product_plan(code,name,description,price,currency,duration_months,enabled)
SELECT 'PROMOTION_MONTHLY','付费投放月套餐','供应商首页推荐与投放服务',1999.00,'CNY',1,1
WHERE NOT EXISTS (SELECT 1 FROM fh_product_plan WHERE code='PROMOTION_MONTHLY');
INSERT INTO fh_product_plan(code,name,description,price,currency,duration_months,enabled)
SELECT 'PROMOTION_YEARLY','付费投放年套餐','供应商首页推荐与投放服务',19900.00,'CNY',12,1
WHERE NOT EXISTS (SELECT 1 FROM fh_product_plan WHERE code='PROMOTION_YEARLY');
