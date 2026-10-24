-- HISTORICAL ONLY: obsolete sys_user account model; do not run with current direct-account backend. See README.md.
-- MySQL 8.0+; import the team's base dump into an EMPTY project database first.
-- Run this version once. No DROP/TRUNCATE and no replacement of existing business records.
CREATE TABLE IF NOT EXISTS backend_schema_version (version INT PRIMARY KEY, applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS sys_bootstrap (id BIGINT PRIMARY KEY);
INSERT INTO sys_bootstrap(id) SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM sys_bootstrap WHERE id=1);
CREATE TABLE IF NOT EXISTS store (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(64) NOT NULL UNIQUE, address VARCHAR(255),
 enabled BOOLEAN NOT NULL DEFAULT TRUE, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO store(name) SELECT DISTINCT dept FROM broker b WHERE dept IS NOT NULL AND dept<>'' AND NOT EXISTS(SELECT 1 FROM store s WHERE s.name=b.dept);
ALTER TABLE broker ADD COLUMN store_id BIGINT NULL, ADD COLUMN user_id BIGINT NULL, ADD UNIQUE KEY uk_broker_user(user_id);
UPDATE broker SET store_id=(SELECT id FROM store WHERE name=broker.dept LIMIT 1) WHERE store_id IS NULL;
ALTER TABLE house ADD COLUMN store_id BIGINT NULL, ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'LEGACY_IMPORT',
 ADD COLUMN verification_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 ADD COLUMN data_origin VARCHAR(32) NOT NULL DEFAULT 'LEGACY_IMPORT',
 ADD COLUMN version BIGINT NOT NULL DEFAULT 0, ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
 ADD KEY idx_house_store_status(store_id,deleted,status);
UPDATE house SET store_id=(SELECT store_id FROM broker WHERE broker.id=house.broker_id) WHERE store_id IS NULL;
ALTER TABLE house_show ADD COLUMN store_id BIGINT NULL, ADD COLUMN customer_id BIGINT NULL,
 ADD COLUMN appointment_time DATETIME NULL, ADD COLUMN completed_time DATETIME NULL,
 ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED', ADD COLUMN cancel_reason VARCHAR(500),
 ADD COLUMN feedback TEXT, ADD COLUMN intention VARCHAR(16), ADD COLUMN follow_up_time DATETIME NULL,
 ADD COLUMN data_origin VARCHAR(32) NOT NULL DEFAULT 'LEGACY_DEMO',
 ADD COLUMN version BIGINT NOT NULL DEFAULT 0, ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
 ADD KEY idx_showing_store_status(store_id,status,show_time), ADD KEY idx_showing_customer(customer_id);
UPDATE house_show SET store_id=(SELECT store_id FROM broker WHERE broker.id=house_show.broker_id),
 completed_time=show_time WHERE data_origin='LEGACY_DEMO';
CREATE TABLE IF NOT EXISTS sys_role (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, code VARCHAR(32) NOT NULL UNIQUE, name VARCHAR(64) NOT NULL,
 permissions LONGTEXT NOT NULL, data_scope VARCHAR(16) NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE IF NOT EXISTS sys_user (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, username VARCHAR(64) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL,
 display_name VARCHAR(64) NOT NULL, phone VARCHAR(32), store_id BIGINT NOT NULL, broker_id BIGINT UNIQUE,
 enabled BOOLEAN NOT NULL DEFAULT TRUE, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(store_id) REFERENCES store(id), FOREIGN KEY(broker_id) REFERENCES broker(id)
);
CREATE TABLE IF NOT EXISTS sys_user_role (
 user_id BIGINT NOT NULL, role_id BIGINT NOT NULL, PRIMARY KEY(user_id,role_id),
 FOREIGN KEY(user_id) REFERENCES sys_user(id), FOREIGN KEY(role_id) REFERENCES sys_role(id)
);
CREATE TABLE IF NOT EXISTS auth_session (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, token_hash VARCHAR(64) NOT NULL UNIQUE, user_id BIGINT NOT NULL,
 expires_at DATETIME NOT NULL, revoked BOOLEAN NOT NULL DEFAULT FALSE, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(user_id) REFERENCES sys_user(id), KEY idx_session_user(user_id,revoked,expires_at)
);
CREATE TABLE IF NOT EXISTS house_media (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, house_id BIGINT NOT NULL, media_type VARCHAR(16) NOT NULL,
 file_name VARCHAR(100) NOT NULL UNIQUE, original_name VARCHAR(255) NOT NULL, content_type VARCHAR(64) NOT NULL,
 file_size BIGINT NOT NULL, uploaded_by BIGINT NOT NULL, deleted BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(house_id) REFERENCES house(id)
);
CREATE TABLE IF NOT EXISTS house_status_log (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, house_id BIGINT NOT NULL, from_status VARCHAR(32), to_status VARCHAR(32) NOT NULL,
 event_type VARCHAR(32) NOT NULL, reason VARCHAR(500) NOT NULL, changed_by BIGINT NOT NULL, store_id BIGINT NOT NULL,
 changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(house_id) REFERENCES house(id), KEY idx_house_history(house_id,id)
);
CREATE TABLE IF NOT EXISTS owner (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(64) NOT NULL, phone VARCHAR(32) NOT NULL, address VARCHAR(255),
 remark TEXT, store_id BIGINT NOT NULL, broker_id BIGINT, version BIGINT NOT NULL DEFAULT 0,
 deleted BOOLEAN NOT NULL DEFAULT FALSE, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(store_id) REFERENCES store(id), FOREIGN KEY(broker_id) REFERENCES broker(id), KEY idx_owner_scope(store_id,broker_id,deleted)
);
CREATE TABLE IF NOT EXISTS owner_entrustment (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, owner_id BIGINT NOT NULL, house_id BIGINT NOT NULL, start_date DATE NOT NULL,
 end_date DATE NOT NULL, is_exclusive BOOLEAN NOT NULL DEFAULT FALSE, commission_rate DECIMAL(6,4),
 status VARCHAR(16) NOT NULL, remark TEXT, end_reason VARCHAR(500), store_id BIGINT NOT NULL, broker_id BIGINT,
 created_by BIGINT NOT NULL, deleted BOOLEAN NOT NULL DEFAULT FALSE, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(owner_id) REFERENCES owner(id), FOREIGN KEY(house_id) REFERENCES house(id), KEY idx_entrustment_due(status,end_date),
 KEY idx_entrustment_scope(store_id,broker_id,deleted)
);
CREATE TABLE IF NOT EXISTS owner_reminder (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, entrustment_id BIGINT NOT NULL UNIQUE, due_date DATE NOT NULL,
 read_status VARCHAR(16) NOT NULL DEFAULT 'UNREAD', read_by BIGINT,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(entrustment_id) REFERENCES owner_entrustment(id)
);
CREATE TABLE IF NOT EXISTS owner_follow_up (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, owner_id BIGINT NOT NULL, contact_type VARCHAR(16) NOT NULL, content TEXT NOT NULL,
 next_contact_at DATETIME, created_by BIGINT NOT NULL, store_id BIGINT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(owner_id) REFERENCES owner(id), KEY idx_owner_follow(owner_id,id)
);
CREATE TABLE IF NOT EXISTS customer (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(64) NOT NULL, phone VARCHAR(32) NOT NULL, tags LONGTEXT NOT NULL,
 remark TEXT, store_id BIGINT NOT NULL, broker_id BIGINT, status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
 version BIGINT NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(store_id) REFERENCES store(id), FOREIGN KEY(broker_id) REFERENCES broker(id), KEY idx_customer_scope(store_id,broker_id,deleted)
);
CREATE TABLE IF NOT EXISTS customer_demand (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, customer_id BIGINT NOT NULL UNIQUE, districts LONGTEXT NOT NULL,
 min_price DECIMAL(16,2), max_price DECIMAL(16,2), min_area DECIMAL(10,2), max_area DECIMAL(10,2),
 min_rooms INT, max_rooms INT, decoration VARCHAR(16), expected_purchase_date DATE, notes TEXT,
 FOREIGN KEY(customer_id) REFERENCES customer(id)
);
CREATE TABLE IF NOT EXISTS customer_follow_up (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, customer_id BIGINT NOT NULL, contact_type VARCHAR(16) NOT NULL, content TEXT NOT NULL,
 next_contact_at DATETIME, created_by BIGINT NOT NULL, store_id BIGINT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(customer_id) REFERENCES customer(id), KEY idx_customer_follow(customer_id,id)
);
CREATE TABLE IF NOT EXISTS showing_follow_up (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, showing_id BIGINT NOT NULL, content TEXT NOT NULL, intention VARCHAR(16) NOT NULL,
 next_contact_at DATETIME, created_by BIGINT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(showing_id) REFERENCES house_show(id)
);
CREATE TABLE IF NOT EXISTS house_deal (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, house_id BIGINT NOT NULL, customer_id BIGINT NOT NULL, broker_id BIGINT NOT NULL,
 store_id BIGINT NOT NULL, deal_time DATETIME NOT NULL, transaction_price_yuan DECIMAL(16,2) NOT NULL,
 commission_yuan DECIMAL(16,2), remark TEXT, status VARCHAR(16) NOT NULL, verified BOOLEAN NOT NULL DEFAULT FALSE,
 verified_by BIGINT, verified_at DATETIME, verify_reason VARCHAR(500), cancel_reason VARCHAR(500),
 created_by BIGINT NOT NULL, version BIGINT NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(house_id) REFERENCES house(id),
 FOREIGN KEY(customer_id) REFERENCES customer(id), FOREIGN KEY(broker_id) REFERENCES broker(id),
 KEY idx_deal_scope(store_id,broker_id,deleted), KEY idx_deal_report(status,verified,deal_time), KEY idx_deal_house(house_id,status)
);
CREATE TABLE IF NOT EXISTS house_text_analysis (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, house_id BIGINT NOT NULL UNIQUE, request_id VARCHAR(128) NOT NULL,
 input_hash VARCHAR(64) NOT NULL, status VARCHAR(16) NOT NULL, summary TEXT,
 advantages LONGTEXT, disadvantages LONGTEXT, word_freq LONGTEXT, error_message VARCHAR(500),
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(house_id) REFERENCES house(id)
);
CREATE TABLE IF NOT EXISTS ai_request_log (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, request_id VARCHAR(128) NOT NULL UNIQUE, house_id BIGINT NOT NULL,
 kind VARCHAR(16) NOT NULL, status VARCHAR(16) NOT NULL, input_snapshot LONGTEXT NOT NULL, result_snapshot LONGTEXT,
 feature_hash VARCHAR(64) NOT NULL, error_message VARCHAR(500), requested_by BIGINT NOT NULL,
 store_id BIGINT NOT NULL, broker_id BIGINT, record_id BIGINT,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(house_id) REFERENCES house(id),
 KEY idx_ai_house(house_id,id), KEY idx_ai_scope(store_id,broker_id,created_at)
);
INSERT INTO sys_role(code,name,data_scope,permissions) SELECT 'ADMIN','管理员','ALL','["system:manage","system:read","house:read","house:write","house:verify","house:assign","owner:read","owner:write","customer:read","customer:write","showing:read","showing:write","deal:read","deal:write","deal:verify","ai:use","report:read"]' WHERE NOT EXISTS(SELECT 1 FROM sys_role WHERE code='ADMIN');
INSERT INTO sys_role(code,name,data_scope,permissions) SELECT 'BROKER','经纪人','OWN','["system:read","house:read","house:write","owner:read","owner:write","customer:read","customer:write","showing:read","showing:write","deal:read","deal:write","ai:use","report:read"]' WHERE NOT EXISTS(SELECT 1 FROM sys_role WHERE code='BROKER');
INSERT INTO backend_schema_version(version) VALUES(1);
