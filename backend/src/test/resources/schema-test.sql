CREATE TABLE broker (

  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '经纪人主键ID',
  `broker_name` varchar(64) NOT NULL COMMENT '经纪人姓名',
  `phone` varchar(32) DEFAULT NULL COMMENT '联系电话',
  `dept` varchar(64) DEFAULT NULL COMMENT '所属门店/部门',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `broker_idx_dept` (`dept`),
 store_id BIGINT NULL,
 user_id BIGINT NULL,
 UNIQUE KEY uk_broker_user(user_id)
);
CREATE TABLE house (

  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '房源主键',
  `city` varchar(32) NOT NULL COMMENT '城市，第一版固定杭州',
  `district` varchar(32) NOT NULL COMMENT '行政区：滨江/临平',
  `business_area` varchar(128) DEFAULT NULL COMMENT '板块，如滨江区政府',
  `community_name` varchar(128) NOT NULL COMMENT '小区名称',
  `community_id` bigint DEFAULT NULL COMMENT '小区主键（预留）',
  `longitude` decimal(10,6) DEFAULT NULL COMMENT '经度',
  `latitude` decimal(10,6) DEFAULT NULL COMMENT '纬度',
  `building_area_sqm` decimal(10,2) NOT NULL COMMENT '建筑面积(㎡)',
  `rooms` smallint DEFAULT NULL COMMENT '室数',
  `halls` smallint DEFAULT NULL COMMENT '厅数，未知留NULL，不要0',
  `bathrooms` smallint DEFAULT NULL COMMENT '卫生间数，未知留NULL，不要0',
  `floor_level` varchar(16) NOT NULL COMMENT '楼层档位 LOW/MIDDLE/HIGH/UNKNOWN',
  `total_floors` smallint NOT NULL COMMENT '楼栋总层数',
  `orientation` LONGTEXT DEFAULT NULL COMMENT '朝向数组 ["S","N"]',
  `decoration` varchar(16) NOT NULL COMMENT '装修 ROUGH/SIMPLE/FINE/OTHER/UNKNOWN',
  `has_elevator` tinyint DEFAULT NULL COMMENT '有无电梯：1有，0无，NULL未知',
  `property_usage` varchar(32) NOT NULL COMMENT '房屋用途 RESIDENTIAL普通住宅',
  `building_type` varchar(32) DEFAULT NULL COMMENT '建筑类型 板楼/塔楼',
  `layout_structure` varchar(32) DEFAULT NULL COMMENT '户型结构 平层/复式',
  `construction_structure` varchar(32) DEFAULT NULL COMMENT '建筑结构',
  `ownership_type` varchar(32) DEFAULT NULL COMMENT '交易权属',
  `internal_area_sqm` decimal(10,2) DEFAULT NULL COMMENT '套内面积',
  `listing_date` date DEFAULT NULL COMMENT '挂牌日期',
  `status_change_time` datetime DEFAULT NULL COMMENT '最后状态变更时间：成交时间/下架时间',
  `listing_price_yuan` decimal(16,2) DEFAULT NULL COMMENT '业主挂牌总价',
  `core_selling` text COMMENT '核心卖点',
  `community_intro` text COMMENT '小区介绍',
  `surrounding` text COMMENT '周边配套',
  `source_listing_id` varchar(64) DEFAULT NULL COMMENT '原始链家编号，用于去重',
  `status` varchar(32) NOT NULL DEFAULT 'ON_SALE' COMMENT '房源状态 ON_SALE在售 / OFF下架',
  `broker_id` bigint DEFAULT NULL COMMENT '归属经纪人ID（关联broker表，演示用）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `house_idx_district` (`district`),
  KEY `house_idx_community` (`community_name`),
  KEY `house_idx_status` (`status`),
  KEY `house_idx_listing_date` (`listing_date`),
  KEY `house_idx_broker` (`broker_id`),
 store_id BIGINT NULL,
 source VARCHAR(32) NOT NULL DEFAULT 'LEGACY_IMPORT',
 verification_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 data_origin VARCHAR(32) NOT NULL DEFAULT 'LEGACY_IMPORT',
 version BIGINT NOT NULL DEFAULT 0,
 deleted BOOLEAN NOT NULL DEFAULT FALSE,
 KEY idx_house_store_status(store_id,deleted,status)
);
CREATE TABLE house_price_pred (

  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '预测记录主键',
  `house_id` bigint DEFAULT NULL COMMENT '关联房源id，房源未保存时可为NULL',
  `request_id` varchar(128) NOT NULL COMMENT '请求追踪编号',
  `requested_by` bigint DEFAULT NULL COMMENT '操作人（经纪人id）',
  `schema_version` varchar(16) NOT NULL COMMENT '接口字段版本',
  `input_snapshot` LONGTEXT NOT NULL COMMENT '本次估价的房源特征快照',
  `feature_hash` varchar(64) NOT NULL COMMENT '特征哈希，判断特征是否变更',
  `status` varchar(32) NOT NULL COMMENT 'DEMO_ONLY参考价 / UNAVAILABLE无法估价',
  `pred_usage` varchar(32) NOT NULL COMMENT '用途，首版HISTORICAL_DEMO',
  `price_basis` varchar(32) NOT NULL COMMENT '价格口径 LISTING_UNVERIFIED',
  `estimated_unit_price_yuan_sqm` decimal(14,2) DEFAULT NULL COMMENT '预估单价 元/㎡',
  `estimated_total_price_yuan` decimal(16,2) DEFAULT NULL COMMENT '预估总价 元',
  `price_interval` LONGTEXT DEFAULT NULL COMMENT '价格区间 {min,max,sample_count...}',
  `reference_level` varchar(32) DEFAULT NULL COMMENT '参考层级 COMMUNITY/BUSINESS_AREA/NONE',
  `comparable_sample_count` int DEFAULT NULL COMMENT '参考样本数量',
  `quality_flags` LONGTEXT DEFAULT NULL COMMENT '数据质量标记',
  `explanations` LONGTEXT DEFAULT NULL COMMENT 'AI解释文本',
  `model_version` varchar(64) NOT NULL COMMENT '模型版本',
  `data_version` varchar(64) NOT NULL COMMENT '数据版本',
  `data_observed_at` datetime DEFAULT NULL COMMENT '原始数据观测时间',
  `generated_at` datetime NOT NULL COMMENT '本次估价生成时间',
  `notice` text COMMENT '提示免责文本',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `house_price_pred_idx_house_id` (`house_id`),
  KEY `house_price_pred_idx_model_version` (`model_version`)
);
CREATE TABLE house_show (

  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '带看记录主键ID',
  `house_id` bigint NOT NULL COMMENT '关联房源ID',
  `broker_id` bigint NOT NULL COMMENT '执行带看的经纪人ID',
  `show_time` datetime NOT NULL COMMENT '带看时间',
  `customer_name` varchar(64) DEFAULT NULL COMMENT '客户姓名（演示用）',
  `remark` text COMMENT '带看备注/回访记录',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  KEY `house_show_idx_house` (`house_id`),
  KEY `house_show_idx_broker` (`broker_id`),
  KEY `house_show_idx_show_time` (`show_time`),
 store_id BIGINT NULL,
 customer_id BIGINT NULL,
 appointment_time DATETIME NULL,
 completed_time DATETIME NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED',
 cancel_reason VARCHAR(500),
 feedback TEXT,
 intention VARCHAR(16),
 follow_up_time DATETIME NULL,
 data_origin VARCHAR(32) NOT NULL DEFAULT 'LEGACY_DEMO',
 version BIGINT NOT NULL DEFAULT 0,
 deleted BOOLEAN NOT NULL DEFAULT FALSE,
 KEY idx_showing_store_status(store_id,status,show_time),
 KEY idx_showing_customer(customer_id)
);
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

UPDATE broker SET store_id=(SELECT id FROM store WHERE name=broker.dept LIMIT 1) WHERE store_id IS NULL;

UPDATE house SET store_id=(SELECT store_id FROM broker WHERE broker.id=house.broker_id) WHERE store_id IS NULL;

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

-- V003 fixture columns (H2 syntax; real MySQL uses the versioned migration).
ALTER TABLE sys_user ALTER COLUMN store_id DROP NOT NULL;
ALTER TABLE sys_user ADD COLUMN account_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE sys_user ADD COLUMN review_reason VARCHAR(500);
ALTER TABLE sys_user ADD COLUMN reviewed_by BIGINT;
ALTER TABLE sys_user ADD COLUMN reviewed_at DATETIME;
ALTER TABLE sys_user ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE house_media ADD COLUMN is_cover BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE house_media ADD COLUMN sort_order INT NOT NULL DEFAULT 0;
ALTER TABLE owner ADD COLUMN next_contact_at DATETIME;
ALTER TABLE customer ADD COLUMN next_contact_at DATETIME;
ALTER TABLE customer ADD COLUMN inactive_reason VARCHAR(500);
ALTER TABLE house_show ADD COLUMN next_contact_at DATETIME;
ALTER TABLE owner_entrustment ADD COLUMN agreement_id BIGINT;
ALTER TABLE house_deal ADD COLUMN verification_status VARCHAR(16) NOT NULL DEFAULT 'PENDING';
ALTER TABLE ai_request_log ADD COLUMN house_version BIGINT;
CREATE TABLE application_session (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, token_hash VARCHAR(64) NOT NULL UNIQUE,
 expires_at DATETIME NOT NULL, revoked BOOLEAN NOT NULL DEFAULT FALSE,
 FOREIGN KEY(user_id) REFERENCES sys_user(id)
);
CREATE TABLE operation_log (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, resource_type VARCHAR(32) NOT NULL, resource_id BIGINT NOT NULL,
 event_type VARCHAR(32) NOT NULL, actor_id BIGINT, reason VARCHAR(500),
 before_snapshot LONGTEXT, after_snapshot LONGTEXT, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_operation_resource(resource_type,resource_id,id)
);
INSERT INTO backend_schema_version(version) VALUES(2);
INSERT INTO backend_schema_version(version) VALUES(3);
