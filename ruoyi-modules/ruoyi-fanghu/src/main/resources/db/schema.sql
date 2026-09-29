-- 方虎业务扩展脚本。首次初始化必须先执行 script/sql/ry_vue.sql。
-- 本脚本不会创建或覆盖 RuoYi 原生 sys_user/sys_role/sys_menu 等系统表。
CREATE DATABASE IF NOT EXISTS fang_hu DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fang_hu;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS fh_topic_category (id BIGINT PRIMARY KEY AUTO_INCREMENT,parent_id BIGINT NOT NULL DEFAULT 0,name VARCHAR(80) NOT NULL,sort_no INT NOT NULL DEFAULT 0,enabled TINYINT NOT NULL DEFAULT 1,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,UNIQUE KEY uk_topic_category(parent_id,name));
CREATE TABLE IF NOT EXISTS fh_taxonomy_node (id BIGINT PRIMARY KEY AUTO_INCREMENT,parent_id BIGINT NOT NULL DEFAULT 0,taxonomy_type VARCHAR(20) NOT NULL,code VARCHAR(60) NOT NULL,name VARCHAR(80) NOT NULL,description VARCHAR(255),sort_no INT NOT NULL DEFAULT 0,enabled TINYINT NOT NULL DEFAULT 1,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,UNIQUE KEY uk_taxonomy_type_code(taxonomy_type,code),UNIQUE KEY uk_taxonomy_parent_name(taxonomy_type,parent_id,name),INDEX idx_taxonomy_tree(taxonomy_type,parent_id,enabled,sort_no));
CREATE TABLE IF NOT EXISTS fh_content_taxonomy (id BIGINT PRIMARY KEY AUTO_INCREMENT,content_type VARCHAR(20) NOT NULL,content_id BIGINT NOT NULL,taxonomy_id BIGINT NOT NULL,primary_flag TINYINT NOT NULL DEFAULT 0,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,UNIQUE KEY uk_content_taxonomy(content_type,content_id,taxonomy_id),INDEX idx_content_taxonomy_node(taxonomy_id,content_type,content_id),INDEX idx_content_taxonomy_content(content_type,content_id));
CREATE TABLE IF NOT EXISTS fh_topic (id BIGINT PRIMARY KEY AUTO_INCREMENT,category_id BIGINT NOT NULL,title VARCHAR(200) NOT NULL,summary VARCHAR(500),cover_url VARCHAR(500),source_desc VARCHAR(255),source_url VARCHAR(500),detail_content LONGTEXT,status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',is_recommended TINYINT NOT NULL DEFAULT 0,published_at DATETIME,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,INDEX idx_topic_category(category_id),INDEX idx_topic_status(status),INDEX idx_topic_recommended(status,is_recommended,published_at));
CREATE TABLE IF NOT EXISTS fh_article_category (id BIGINT PRIMARY KEY AUTO_INCREMENT,name VARCHAR(80) NOT NULL,sort_no INT NOT NULL DEFAULT 0,enabled TINYINT NOT NULL DEFAULT 1);
INSERT INTO fh_article_category(id,name,sort_no,enabled) VALUES
(1,'政策与行业动态',10,1),(2,'装备技术',20,1),(3,'市场与采购',30,1),(4,'任务应用',40,1)
ON DUPLICATE KEY UPDATE name=VALUES(name),sort_no=VALUES(sort_no),enabled=VALUES(enabled);
CREATE TABLE IF NOT EXISTS fh_article (id BIGINT PRIMARY KEY AUTO_INCREMENT,category_id BIGINT NOT NULL,title VARCHAR(200) NOT NULL,summary VARCHAR(500),image_url VARCHAR(500),content LONGTEXT NOT NULL,source_name VARCHAR(120),source_url VARCHAR(500),detail_content LONGTEXT,status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',is_hot TINYINT NOT NULL DEFAULT 0,published_at DATETIME,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,INDEX idx_article_category(category_id),INDEX idx_article_hot(is_hot,status));
CREATE TABLE IF NOT EXISTS fh_supplier_company (id BIGINT PRIMARY KEY AUTO_INCREMENT,owner_user_id BIGINT NOT NULL,name VARCHAR(200) NOT NULL,credit_code VARCHAR(40),intro TEXT,verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',service_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',rating_score DECIMAL(5,2) NOT NULL DEFAULT 0,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,UNIQUE KEY uk_supplier_owner(owner_user_id),UNIQUE KEY uk_supplier_credit(credit_code));
CREATE TABLE IF NOT EXISTS fh_supplier_membership (id BIGINT PRIMARY KEY AUTO_INCREMENT,supplier_id BIGINT NOT NULL,source_type VARCHAR(20) NOT NULL,plan_name VARCHAR(120) NOT NULL,fee_amount DECIMAL(12,2),payment_status VARCHAR(20) NOT NULL,status VARCHAR(20) NOT NULL,placement_enabled TINYINT NOT NULL DEFAULT 0,starts_at DATETIME,ends_at DATETIME,operator_user_id BIGINT,note VARCHAR(500),created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,INDEX idx_membership_supplier(supplier_id,created_at),INDEX idx_membership_status(status,ends_at),INDEX idx_membership_payment(payment_status,created_at));
CREATE TABLE IF NOT EXISTS fh_solution (id BIGINT PRIMARY KEY AUTO_INCREMENT,supplier_id BIGINT NOT NULL,title VARCHAR(200) NOT NULL,category_id BIGINT NOT NULL,scene_tags VARCHAR(500),summary VARCHAR(500),content LONGTEXT,audit_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',visibility VARCHAR(20) NOT NULL DEFAULT 'AUTHORIZED',created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,INDEX idx_solution_category(category_id),INDEX idx_solution_supplier(supplier_id));
CREATE TABLE IF NOT EXISTS fh_customer_profile (id BIGINT PRIMARY KEY AUTO_INCREMENT,user_id BIGINT NOT NULL,organization VARCHAR(200),contact_name VARCHAR(80) NOT NULL,contact_phone VARCHAR(30),verification_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,UNIQUE KEY uk_customer_user(user_id));
CREATE TABLE IF NOT EXISTS fh_demand (id BIGINT PRIMARY KEY AUTO_INCREMENT,customer_id BIGINT NOT NULL,title VARCHAR(200) NOT NULL,description TEXT NOT NULL,category_id BIGINT,budget_level VARCHAR(30),status VARCHAR(20) NOT NULL DEFAULT 'NEW',created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,INDEX idx_demand_customer(customer_id));
CREATE TABLE IF NOT EXISTS fh_lead (id BIGINT PRIMARY KEY AUTO_INCREMENT,demand_id BIGINT NOT NULL,customer_id BIGINT NOT NULL,supplier_id BIGINT NOT NULL,status VARCHAR(20) NOT NULL DEFAULT 'NEW',match_score DECIMAL(5,2),customer_confirmation VARCHAR(20) NOT NULL DEFAULT 'PENDING',supplier_confirmation VARCHAR(20) NOT NULL DEFAULT 'PENDING',customer_confirmed_at DATETIME,supplier_confirmed_at DATETIME,operator_user_id BIGINT,remark VARCHAR(500),next_action_at DATETIME,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,UNIQUE KEY uk_demand_supplier(demand_id,supplier_id),INDEX idx_lead_customer(customer_id),INDEX idx_lead_supplier(supplier_id),INDEX idx_lead_status(status),INDEX idx_lead_operator(operator_user_id));
CREATE TABLE IF NOT EXISTS fh_lead_event (id BIGINT PRIMARY KEY AUTO_INCREMENT,lead_id BIGINT NOT NULL,action VARCHAR(40) NOT NULL,before_status VARCHAR(20),after_status VARCHAR(20),remark VARCHAR(500),actor_user_id BIGINT,actor_name VARCHAR(64) NOT NULL,actor_role VARCHAR(20) NOT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,INDEX idx_lead_event_lead(lead_id,created_at),INDEX idx_lead_event_actor(actor_user_id));
CREATE TABLE IF NOT EXISTS fh_file_asset (id BIGINT PRIMARY KEY AUTO_INCREMENT,owner_type VARCHAR(30) NOT NULL,owner_id BIGINT NOT NULL,object_key VARCHAR(500) NOT NULL,sha256 CHAR(64),visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',scan_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',download_count INT NOT NULL DEFAULT 0,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,INDEX idx_file_owner(owner_type,owner_id));
CREATE TABLE IF NOT EXISTS fh_file_download_log (id BIGINT PRIMARY KEY AUTO_INCREMENT,file_id BIGINT NOT NULL,user_id BIGINT NOT NULL,user_role VARCHAR(30) NOT NULL,ip_address VARCHAR(64),user_agent VARCHAR(500),result VARCHAR(20) NOT NULL,expires_at DATETIME,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,INDEX idx_file_download_file(file_id,created_at),INDEX idx_file_download_user(user_id,created_at));
CREATE TABLE IF NOT EXISTS fh_ad_slot (id BIGINT PRIMARY KEY AUTO_INCREMENT,slot_code VARCHAR(60) NOT NULL,title VARCHAR(120) NOT NULL,image_url VARCHAR(500),target_url VARCHAR(500),start_at DATETIME,end_at DATETIME,status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',sort_no INT NOT NULL DEFAULT 0,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,UNIQUE KEY uk_ad_slot(slot_code));
CREATE TABLE IF NOT EXISTS fh_platform_contact (id BIGINT PRIMARY KEY,hotline VARCHAR(40),email VARCHAR(120),address VARCHAR(255),service_hours VARCHAR(120),created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS fh_supplier_metric (id BIGINT PRIMARY KEY AUTO_INCREMENT,supplier_id BIGINT NOT NULL,metric_date DATE NOT NULL,impressions INT NOT NULL DEFAULT 0,views INT NOT NULL DEFAULT 0,inquiries INT NOT NULL DEFAULT 0,downloads INT NOT NULL DEFAULT 0,qualified_leads INT NOT NULL DEFAULT 0,won_leads INT NOT NULL DEFAULT 0,UNIQUE KEY uk_supplier_metric(supplier_id,metric_date));
CREATE TABLE IF NOT EXISTS fh_ai_job (id BIGINT PRIMARY KEY AUTO_INCREMENT,requester_id BIGINT NOT NULL,title VARCHAR(200) NOT NULL,source_ids JSON,status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',toc_json JSON,result_file_id BIGINT,access_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUESTED',access_reason VARCHAR(500),decided_by BIGINT,decided_at DATETIME,generator VARCHAR(80),expires_at DATETIME,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,INDEX idx_ai_job_requester(requester_id,created_at),INDEX idx_ai_job_access(access_status,created_at),INDEX idx_ai_job_status(status,created_at));
CREATE TABLE IF NOT EXISTS fh_ai_report_content (job_id BIGINT PRIMARY KEY,content LONGTEXT NOT NULL,generator VARCHAR(80) NOT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS fh_audit_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  biz_type VARCHAR(20) NOT NULL,
  biz_id BIGINT NOT NULL,
  action VARCHAR(20) NOT NULL,
  before_status VARCHAR(20) NOT NULL,
  after_status VARCHAR(20) NOT NULL,
  opinion VARCHAR(1000),
  operator_user_id BIGINT NOT NULL,
  operator_name VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_audit_biz (biz_type,biz_id),
  INDEX idx_audit_created (created_at),
  INDEX idx_audit_operator (operator_user_id)
);
INSERT IGNORE INTO fh_topic_category(parent_id,name,sort_no,enabled) VALUES
(0,'车辆与机动平台',10,1),(0,'通信与指挥系统',20,1),(0,'无人系统',30,1),
(0,'侦察与感知设备',40,1),(0,'防护与保障装备',50,1),(0,'训练与仿真系统',60,1);
INSERT INTO fh_taxonomy_node(id,parent_id,taxonomy_type,code,name,description,sort_no,enabled) VALUES
(1,0,'EQUIPMENT','vehicle','车辆与机动平台','车辆、底盘与特种机动平台',10,1),(2,0,'EQUIPMENT','command','通信与指挥系统','通信、组网和指挥控制装备',20,1),(3,0,'EQUIPMENT','unmanned','无人系统','无人机、无人车和无人平台',30,1),(4,0,'EQUIPMENT','sensing','侦察与感知设备','探测、侦察和态势感知装备',40,1),(5,0,'EQUIPMENT','protection','防护与保障装备','个体防护、工程保障和后勤装备',50,1),(6,0,'EQUIPMENT','training','训练与仿真系统','训练器材和仿真推演系统',60,1),
(11,1,'EQUIPMENT','tracked-platform','履带式平台',NULL,10,1),(12,1,'EQUIPMENT','wheeled-platform','轮式平台',NULL,20,1),(21,2,'EQUIPMENT','tactical-radio','战术通信',NULL,10,1),(22,2,'EQUIPMENT','command-platform','指挥控制平台',NULL,20,1),(31,3,'EQUIPMENT','uav','无人机系统',NULL,10,1),(32,3,'EQUIPMENT','ugv','无人地面平台',NULL,20,1),(41,4,'EQUIPMENT','radar','雷达探测',NULL,10,1),(42,4,'EQUIPMENT','optoelectronic','光电侦察',NULL,20,1),(51,5,'EQUIPMENT','individual-protection','个体防护',NULL,10,1),(52,5,'EQUIPMENT','field-support','野外保障',NULL,20,1),(61,6,'EQUIPMENT','simulation','模拟训练',NULL,10,1),(62,6,'EQUIPMENT','evaluation','训练评估',NULL,20,1),
(101,0,'TOPIC','equipment-system','装备体系','按装备体系汇总专题',10,1),(102,0,'TOPIC','technology-frontier','技术前沿','按关键技术方向汇总专题',20,1),(103,0,'TOPIC','application-study','应用研究','按应用需求汇总专题',30,1),(111,101,'TOPIC','land-system','陆上装备体系',NULL,10,1),(112,101,'TOPIC','air-system','空中装备体系',NULL,20,1),(121,102,'TOPIC','intelligent-system','智能化技术',NULL,10,1),(122,102,'TOPIC','information-system','信息化技术',NULL,20,1),(131,103,'TOPIC','border-defense','边海防应用',NULL,10,1),(132,103,'TOPIC','emergency-support','应急保障应用',NULL,20,1),
(201,0,'SCENE','operation','任务运用','典型任务与行动场景',10,1),(202,0,'SCENE','environment','环境条件','装备工作的自然和电磁环境',20,1),(211,201,'SCENE','reconnaissance','侦察监视',NULL,10,1),(212,201,'SCENE','command-coordination','指挥协同',NULL,20,1),(213,201,'SCENE','logistics','保障运输',NULL,30,1),(221,202,'SCENE','all-weather','全天候',NULL,10,1),(222,202,'SCENE','complex-terrain','复杂地形',NULL,20,1),(223,202,'SCENE','electromagnetic','复杂电磁环境',NULL,30,1),
(301,0,'TECH','information','信息技术','通信、计算和数据技术',10,1),(302,0,'TECH','platform','平台技术','结构、动力和控制技术',20,1),(303,0,'TECH','material','材料与防护','材料、工艺和防护技术',30,1),(311,301,'TECH','ai','人工智能',NULL,10,1),(312,301,'TECH','data-fusion','多源数据融合',NULL,20,1),(313,301,'TECH','secure-communication','安全通信',NULL,30,1),(321,302,'TECH','autonomous-control','自主控制',NULL,10,1),(322,302,'TECH','navigation','组合导航',NULL,20,1),(331,303,'TECH','lightweight-material','轻量化材料',NULL,10,1),(332,303,'TECH','protective-material','防护材料',NULL,20,1)
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),name=VALUES(name),description=VALUES(description),sort_no=VALUES(sort_no);
INSERT INTO fh_platform_contact(id,hotline,email,address,service_hours) VALUES(1,NULL,NULL,NULL,NULL)
ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO fh_ad_slot(slot_code,title,image_url,target_url,start_at,end_at,status,sort_no)
VALUES('HOME_SUPPLIER_ENTRY','供应商能力入驻与方案发布',NULL,'/supplier/apply',NULL,NULL,'ACTIVE',10)
ON DUPLICATE KEY UPDATE target_url=VALUES(target_url);

-- RuoYi 原生角色。平台管理员拥有全部业务权限；客户和供应商按主体隔离业务数据。
INSERT INTO sys_role(role_id,role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_time,remark)
SELECT 1900100000000000001,'平台管理员','admin',10,'1',1,1,'0','0',NOW(),'平台运营与审核'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key='admin');
INSERT INTO sys_role(role_id,role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_time,remark)
SELECT 1900100000000000002,'供应商','supplier',20,'5',1,1,'0','0',NOW(),'供应商企业账号'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key='supplier');
INSERT INTO sys_role(role_id,role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_time,remark)
SELECT 1900100000000000003,'客户','customer',30,'5',1,1,'0','0',NOW(),'采购客户账号'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key='customer');

-- 标准业务菜单与权限点。component 对应交付管理端中的 Vue 页面路径。
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query_param,is_frame,is_cache,menu_type,visible,status,perms,icon,active_menu,ext,create_time,remark) VALUES
(1900200000000000000,'平台业务',0,2,'fanghu',NULL,'','N','Y','M','0','0','','component','','',NOW(),'方虎平台业务目录'),
(1900200000000000100,'专题管理',1900200000000000000,1,'topics','fanghu/topic/index','','N','Y','C','0','0','platform:read','list','','',NOW(),'专题分类与内容管理'),
(1900200000000000101,'资讯管理',1900200000000000000,2,'articles','fanghu/article/index','','N','Y','C','0','0','platform:read','documentation','','',NOW(),'行业资讯管理'),
(1900200000000000102,'供应商管理',1900200000000000000,3,'suppliers','fanghu/supplier/index','','N','Y','C','0','0','platform:read','peoples','','',NOW(),'供应商资质与会员管理'),
(1900200000000000103,'方案管理',1900200000000000000,4,'solutions','fanghu/solution/index','','N','Y','C','0','0','platform:read','tree-table','','',NOW(),'供应商解决方案管理'),
(1900200000000000104,'采购需求',1900200000000000000,5,'demands','fanghu/demand/index','','N','Y','C','0','0','platform:read','form','','',NOW(),'客户采购需求管理'),
(1900200000000000105,'线索运营',1900200000000000000,6,'leads','fanghu/lead/index','','N','Y','C','0','0','platform:read','guide','','',NOW(),'供需线索闭环管理'),
(1900200000000000106,'审核中心',1900200000000000000,7,'audits','fanghu/audit/index','','N','Y','C','0','0','platform:audit','validCode','','',NOW(),'内容与资质审核'),
(1900200000000000107,'分类与字典',1900200000000000000,8,'taxonomy','fanghu/taxonomy/index','','N','Y','C','0','0','platform:read','dict','','',NOW(),'装备、场景与技术标签'),
(1900200000000000108,'首页运营',1900200000000000000,9,'homepage','fanghu/homepage/index','','N','Y','C','0','0','platform:read','dashboard','','',NOW(),'广告、推荐与联系方式'),
(1900200000000000109,'文件安全',1900200000000000000,10,'files','fanghu/file/index','','N','Y','C','0','0','platform:read','upload','','',NOW(),'私有文件、扫描与下载日志'),
(1900200000000000110,'AI汇报',1900200000000000000,11,'reports','fanghu/report/index','','N','Y','C','0','0','platform:read','checkbox','','',NOW(),'AI汇报任务与授权'),
(1900200000000001000,'平台读取',1900200000000000000,1,'','','','N','Y','F','0','0','platform:read','#','','',NOW(),'平台基础读取权限'),
(1900200000000001001,'平台编辑',1900200000000000000,2,'','','','N','Y','F','0','0','platform:write','#','','',NOW(),'平台业务编辑权限'),
(1900200000000001002,'平台审核',1900200000000000000,3,'','','','N','Y','F','0','0','platform:audit','#','','',NOW(),'平台审核权限')
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name),parent_id=VALUES(parent_id),order_num=VALUES(order_num),component=VALUES(component),perms=VALUES(perms),status=VALUES(status);

-- 平台管理员：全部业务菜单与权限。
INSERT IGNORE INTO sys_role_menu(role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m
WHERE r.role_key='admin' AND m.menu_id BETWEEN 1900200000000000000 AND 1900200000000001999;
-- 供应商：企业、方案、线索、文件和汇报工作台。
INSERT IGNORE INTO sys_role_menu(role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m
WHERE r.role_key='supplier' AND m.menu_id IN (1900200000000000000,1900200000000000102,1900200000000000103,1900200000000000105,1900200000000000109,1900200000000000110,1900200000000001000);
-- 客户：专题资讯、需求、线索和汇报工作台。
INSERT IGNORE INTO sys_role_menu(role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m
WHERE r.role_key='customer' AND m.menu_id IN (1900200000000000000,1900200000000000100,1900200000000000101,1900200000000000104,1900200000000000105,1900200000000000110,1900200000000001000);

-- 开启原生自助注册，注册服务会根据 accountType 分配 customer/supplier 角色。
UPDATE sys_config SET config_value='true',update_time=NOW() WHERE config_key='sys.account.registerUser';

-- 方虎业务字典。
INSERT INTO sys_dict_type(dict_id,dict_name,dict_type,create_time,remark) VALUES
(1900300000000000001,'审核状态','fh_audit_status',NOW(),'专题、资讯、方案与资质审核状态'),
(1900300000000000002,'线索状态','fh_lead_status',NOW(),'客户需求与供应商线索闭环状态'),
(1900300000000000003,'文件扫描状态','fh_scan_status',NOW(),'ClamAV 文件安全扫描状态'),
(1900300000000000004,'主体确认状态','fh_confirmation_status',NOW(),'客户和供应商意向确认状态')
ON DUPLICATE KEY UPDATE dict_name=VALUES(dict_name),remark=VALUES(remark);

INSERT INTO sys_dict_data(dict_code,dict_sort,dict_label,dict_value,dict_type,css_class,list_class,is_default,create_time,remark) VALUES
(1900310000000000001,1,'草稿','DRAFT','fh_audit_status','','info','Y',NOW(),''),
(1900310000000000002,2,'待审核','PENDING','fh_audit_status','','warning','N',NOW(),''),
(1900310000000000003,3,'已通过','APPROVED','fh_audit_status','','success','N',NOW(),''),
(1900310000000000004,4,'已驳回','REJECTED','fh_audit_status','','danger','N',NOW(),''),
(1900310000000000005,5,'已下线','OFFLINE','fh_audit_status','','info','N',NOW(),''),
(1900310000000000011,1,'新线索','NEW','fh_lead_status','','info','Y',NOW(),''),
(1900310000000000012,2,'已匹配','MATCHED','fh_lead_status','','primary','N',NOW(),''),
(1900310000000000013,3,'已联系','CONTACTED','fh_lead_status','','warning','N',NOW(),''),
(1900310000000000014,4,'有意向','INTENT','fh_lead_status','','primary','N',NOW(),''),
(1900310000000000015,5,'已成交','WON','fh_lead_status','','success','N',NOW(),''),
(1900310000000000016,6,'已关闭','LOST','fh_lead_status','','danger','N',NOW(),''),
(1900310000000000021,1,'待扫描','PENDING','fh_scan_status','','warning','Y',NOW(),''),
(1900310000000000022,2,'扫描中','RUNNING','fh_scan_status','','primary','N',NOW(),''),
(1900310000000000023,3,'安全','PASSED','fh_scan_status','','success','N',NOW(),''),
(1900310000000000024,4,'已阻断','FAILED','fh_scan_status','','danger','N',NOW(),''),
(1900310000000000031,1,'待确认','PENDING','fh_confirmation_status','','warning','Y',NOW(),''),
(1900310000000000032,2,'已接受','ACCEPTED','fh_confirmation_status','','success','N',NOW(),''),
(1900310000000000033,3,'已拒绝','DECLINED','fh_confirmation_status','','danger','N',NOW(),'')
ON DUPLICATE KEY UPDATE dict_label=VALUES(dict_label),dict_value=VALUES(dict_value),list_class=VALUES(list_class);
