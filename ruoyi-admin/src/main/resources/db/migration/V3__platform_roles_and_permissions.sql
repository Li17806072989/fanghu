-- Platform roles and permission points. No default administrator account is created.
INSERT INTO sys_role(role_id,role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_time,remark)
SELECT 1900100000000000001,'平台管理员','admin',10,'1',1,1,'0','0',NOW(),'平台运营与审核'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key='admin');
INSERT INTO sys_role(role_id,role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_time,remark)
SELECT 1900100000000000002,'供应商','supplier',20,'5',1,1,'0','0',NOW(),'供应商企业账号'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key='supplier');
INSERT INTO sys_role(role_id,role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_time,remark)
SELECT 1900100000000000003,'客户','customer',30,'5',1,1,'0','0',NOW(),'采购客户账号'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key='customer');

INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query_param,is_frame,is_cache,menu_type,visible,status,perms,icon,active_menu,ext,create_time,remark) VALUES
(1900200000000000000,'平台业务',0,2,'fanghu',NULL,'','N','Y','M','0','0','','component','','',NOW(),'平台业务目录'),
(1900200000000001000,'平台读取',1900200000000000000,1,'','','','N','Y','F','0','0','platform:read','#','','',NOW(),'平台基础读取权限'),
(1900200000000001001,'平台编辑',1900200000000000000,2,'','','','N','Y','F','0','0','platform:write','#','','',NOW(),'平台运营编辑权限'),
(1900200000000001002,'平台审核',1900200000000000000,3,'','','','N','Y','F','0','0','platform:audit','#','','',NOW(),'平台审核权限')
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name),parent_id=VALUES(parent_id),perms=VALUES(perms),status=VALUES(status);

INSERT IGNORE INTO sys_role_menu(role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r CROSS JOIN sys_menu m
WHERE r.role_key='admin' AND m.menu_id IN (1900200000000000000,1900200000000001000,1900200000000001001,1900200000000001002);
INSERT IGNORE INTO sys_role_menu(role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r CROSS JOIN sys_menu m
WHERE r.role_key IN ('supplier','customer') AND m.menu_id IN (1900200000000000000,1900200000000001000);
