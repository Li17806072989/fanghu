USE fang_hu;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS fh_taxonomy_node (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT NOT NULL DEFAULT 0,
  taxonomy_type VARCHAR(20) NOT NULL,
  code VARCHAR(60) NOT NULL,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(255),
  sort_no INT NOT NULL DEFAULT 0,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_taxonomy_type_code(taxonomy_type,code),
  UNIQUE KEY uk_taxonomy_parent_name(taxonomy_type,parent_id,name),
  INDEX idx_taxonomy_tree(taxonomy_type,parent_id,enabled,sort_no)
);

CREATE TABLE IF NOT EXISTS fh_content_taxonomy (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  content_type VARCHAR(20) NOT NULL,
  content_id BIGINT NOT NULL,
  taxonomy_id BIGINT NOT NULL,
  primary_flag TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_content_taxonomy(content_type,content_id,taxonomy_id),
  INDEX idx_content_taxonomy_node(taxonomy_id,content_type,content_id),
  INDEX idx_content_taxonomy_content(content_type,content_id)
);

INSERT INTO fh_taxonomy_node(id,parent_id,taxonomy_type,code,name,description,sort_no,enabled) VALUES
(1,0,'EQUIPMENT','vehicle','车辆与机动平台','车辆、底盘与特种机动平台',10,1),
(2,0,'EQUIPMENT','command','通信与指挥系统','通信、组网和指挥控制装备',20,1),
(3,0,'EQUIPMENT','unmanned','无人系统','无人机、无人车和无人平台',30,1),
(4,0,'EQUIPMENT','sensing','侦察与感知设备','探测、侦察和态势感知装备',40,1),
(5,0,'EQUIPMENT','protection','防护与保障装备','个体防护、工程保障和后勤装备',50,1),
(6,0,'EQUIPMENT','training','训练与仿真系统','训练器材和仿真推演系统',60,1),
(11,1,'EQUIPMENT','tracked-platform','履带式平台',NULL,10,1),
(12,1,'EQUIPMENT','wheeled-platform','轮式平台',NULL,20,1),
(21,2,'EQUIPMENT','tactical-radio','战术通信',NULL,10,1),
(22,2,'EQUIPMENT','command-platform','指挥控制平台',NULL,20,1),
(31,3,'EQUIPMENT','uav','无人机系统',NULL,10,1),
(32,3,'EQUIPMENT','ugv','无人地面平台',NULL,20,1),
(41,4,'EQUIPMENT','radar','雷达探测',NULL,10,1),
(42,4,'EQUIPMENT','optoelectronic','光电侦察',NULL,20,1),
(51,5,'EQUIPMENT','individual-protection','个体防护',NULL,10,1),
(52,5,'EQUIPMENT','field-support','野外保障',NULL,20,1),
(61,6,'EQUIPMENT','simulation','模拟训练',NULL,10,1),
(62,6,'EQUIPMENT','evaluation','训练评估',NULL,20,1),
(101,0,'TOPIC','equipment-system','装备体系','按装备体系汇总专题',10,1),
(102,0,'TOPIC','technology-frontier','技术前沿','按关键技术方向汇总专题',20,1),
(103,0,'TOPIC','application-study','应用研究','按应用需求汇总专题',30,1),
(111,101,'TOPIC','land-system','陆上装备体系',NULL,10,1),
(112,101,'TOPIC','air-system','空中装备体系',NULL,20,1),
(121,102,'TOPIC','intelligent-system','智能化技术',NULL,10,1),
(122,102,'TOPIC','information-system','信息化技术',NULL,20,1),
(131,103,'TOPIC','border-defense','边海防应用',NULL,10,1),
(132,103,'TOPIC','emergency-support','应急保障应用',NULL,20,1),
(201,0,'SCENE','operation','任务运用','典型任务与行动场景',10,1),
(202,0,'SCENE','environment','环境条件','装备工作的自然和电磁环境',20,1),
(211,201,'SCENE','reconnaissance','侦察监视',NULL,10,1),
(212,201,'SCENE','command-coordination','指挥协同',NULL,20,1),
(213,201,'SCENE','logistics','保障运输',NULL,30,1),
(221,202,'SCENE','all-weather','全天候',NULL,10,1),
(222,202,'SCENE','complex-terrain','复杂地形',NULL,20,1),
(223,202,'SCENE','electromagnetic','复杂电磁环境',NULL,30,1),
(301,0,'TECH','information','信息技术','通信、计算和数据技术',10,1),
(302,0,'TECH','platform','平台技术','结构、动力和控制技术',20,1),
(303,0,'TECH','material','材料与防护','材料、工艺和防护技术',30,1),
(311,301,'TECH','ai','人工智能',NULL,10,1),
(312,301,'TECH','data-fusion','多源数据融合',NULL,20,1),
(313,301,'TECH','secure-communication','安全通信',NULL,30,1),
(321,302,'TECH','autonomous-control','自主控制',NULL,10,1),
(322,302,'TECH','navigation','组合导航',NULL,20,1),
(331,303,'TECH','lightweight-material','轻量化材料',NULL,10,1),
(332,303,'TECH','protective-material','防护材料',NULL,20,1)
ON DUPLICATE KEY UPDATE
  parent_id=VALUES(parent_id),
  name=VALUES(name),
  description=VALUES(description),
  sort_no=VALUES(sort_no);

INSERT IGNORE INTO fh_content_taxonomy(content_type,content_id,taxonomy_id,primary_flag)
SELECT 'TOPIC',t.id,t.category_id,1 FROM fh_topic t
JOIN fh_taxonomy_node n ON n.id=t.category_id AND n.taxonomy_type='EQUIPMENT';

INSERT IGNORE INTO fh_content_taxonomy(content_type,content_id,taxonomy_id,primary_flag)
SELECT 'SOLUTION',s.id,s.category_id,1 FROM fh_solution s
JOIN fh_taxonomy_node n ON n.id=s.category_id AND n.taxonomy_type='EQUIPMENT';
