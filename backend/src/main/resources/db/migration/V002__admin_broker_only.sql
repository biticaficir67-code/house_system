-- MySQL 8.0+; run after V001, with the application stopped and a database backup.
-- Applies to both fresh V001 installs and the previous five-role V001.
-- Existing ADMIN users remain administrators. Every other existing user becomes BROKER.
-- No user, broker, house, owner, customer, showing or deal records are deleted.
START TRANSACTION;
SELECT id FROM sys_bootstrap WHERE id=1 FOR UPDATE;

INSERT INTO sys_role(code,name,data_scope,permissions)
SELECT 'ADMIN','管理员','ALL','["system:manage","system:read","house:read","house:write","house:verify","house:assign","owner:read","owner:write","customer:read","customer:write","showing:read","showing:write","deal:read","deal:write","deal:verify","ai:use","report:read"]'
WHERE NOT EXISTS(SELECT 1 FROM sys_role WHERE code='ADMIN');
INSERT INTO sys_role(code,name,data_scope,permissions)
SELECT 'BROKER','经纪人','OWN','["system:read","house:read","house:write","owner:read","owner:write","customer:read","customer:write","showing:read","showing:write","deal:read","deal:write","ai:use","report:read"]'
WHERE NOT EXISTS(SELECT 1 FROM sys_role WHERE code='BROKER');
UPDATE sys_role SET name='管理员',data_scope='ALL',enabled=TRUE,
 permissions='["system:manage","system:read","house:read","house:write","house:verify","house:assign","owner:read","owner:write","customer:read","customer:write","showing:read","showing:write","deal:read","deal:write","deal:verify","ai:use","report:read"]' WHERE code='ADMIN';
UPDATE sys_role SET name='经纪人',data_scope='OWN',enabled=TRUE,
 permissions='["system:read","house:read","house:write","owner:read","owner:write","customer:read","customer:write","showing:read","showing:write","deal:read","deal:write","ai:use","report:read"]' WHERE code='BROKER';

-- Keep only ADMIN associations first; this also collapses old multi-role accounts.
DELETE ur FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE r.code<>'ADMIN';
INSERT INTO sys_user_role(user_id,role_id)
SELECT u.id,r.id FROM sys_user u JOIN sys_role r ON r.code='BROKER'
WHERE NOT EXISTS(SELECT 1 FROM sys_user_role ur WHERE ur.user_id=u.id);
UPDATE sys_role SET enabled=FALSE,permissions='[]',data_scope='OWN'
WHERE code NOT IN ('ADMIN','BROKER');

-- Reuse historical broker records; fill only missing two-way account links.
UPDATE broker b JOIN sys_user u ON u.broker_id=b.id
SET b.user_id=u.id WHERE b.user_id IS NULL;
INSERT INTO broker(broker_name,phone,dept,store_id,user_id)
SELECT u.display_name,u.phone,s.name,u.store_id,u.id
FROM sys_user u JOIN store s ON s.id=u.store_id
JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id AND r.code='BROKER'
WHERE u.broker_id IS NULL AND NOT EXISTS(SELECT 1 FROM broker b WHERE b.user_id=u.id);
UPDATE sys_user u JOIN broker b ON b.user_id=u.id
JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id AND r.code='BROKER'
SET u.broker_id=b.id WHERE u.broker_id IS NULL;

-- Permissions changed; require a fresh login for all previous sessions.
UPDATE auth_session SET revoked=TRUE WHERE revoked=FALSE;
INSERT INTO backend_schema_version(version)
SELECT 2 WHERE NOT EXISTS(SELECT 1 FROM backend_schema_version WHERE version=2);
COMMIT;
