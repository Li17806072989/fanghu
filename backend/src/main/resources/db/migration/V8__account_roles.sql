USE fang_hu;

INSERT INTO sys_role(role_name,role_key,status) VALUES
('平台管理员','admin','0'),
('供应商','supplier','0'),
('客户','customer','0')
ON DUPLICATE KEY UPDATE role_name=VALUES(role_name),status=VALUES(status);

-- 旧版注册页仅面向供应商，因此将尚未分配角色的历史账号迁移为供应商。
INSERT IGNORE INTO sys_user_role(user_id,role_id)
SELECT u.user_id,r.role_id
FROM sys_user u
JOIN sys_role r ON r.role_key='supplier'
WHERE u.user_name<>'admin'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id=u.user_id);
