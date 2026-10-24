-- HISTORICAL ONLY: obsolete sys_user account model; do not run with current direct-account backend. See README.md.
-- MySQL 8.0+; stop the backend, back up the database, run ONCE after V001 and V002.
ALTER TABLE sys_user MODIFY store_id BIGINT NULL,
 ADD account_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
 ADD review_reason VARCHAR(500), ADD reviewed_by BIGINT, ADD reviewed_at DATETIME,
 ADD version BIGINT NOT NULL DEFAULT 0;
UPDATE sys_user SET account_status=CASE WHEN enabled=1 THEN 'ACTIVE' ELSE 'DISABLED' END;
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
ALTER TABLE house_media ADD is_cover BOOLEAN NOT NULL DEFAULT FALSE, ADD sort_order INT NOT NULL DEFAULT 0;
UPDATE house_media m JOIN (SELECT house_id,MIN(id) id FROM house_media WHERE deleted=0 AND media_type='IMAGE' GROUP BY house_id) x ON x.id=m.id SET m.is_cover=TRUE;
ALTER TABLE owner ADD next_contact_at DATETIME;
ALTER TABLE customer ADD next_contact_at DATETIME, ADD inactive_reason VARCHAR(500);
ALTER TABLE house_show ADD next_contact_at DATETIME;
UPDATE house_show s JOIN (SELECT showing_id,MAX(id) id FROM showing_follow_up GROUP BY showing_id) x ON x.showing_id=s.id
 JOIN showing_follow_up f ON f.id=x.id SET s.next_contact_at=f.next_contact_at;
UPDATE owner o JOIN (SELECT owner_id,MAX(id) id FROM owner_follow_up GROUP BY owner_id) x ON x.owner_id=o.id
 JOIN owner_follow_up f ON f.id=x.id SET o.next_contact_at=f.next_contact_at;
UPDATE customer c JOIN (SELECT customer_id,MAX(id) id FROM customer_follow_up GROUP BY customer_id) x ON x.customer_id=c.id
 JOIN customer_follow_up f ON f.id=x.id SET c.next_contact_at=f.next_contact_at;
ALTER TABLE owner_entrustment ADD agreement_id BIGINT;
UPDATE owner_entrustment SET agreement_id=id;
ALTER TABLE house_deal ADD verification_status VARCHAR(16) NOT NULL DEFAULT 'PENDING';
UPDATE house_deal SET verification_status=CASE WHEN verified=1 THEN 'APPROVED' WHEN verified_at IS NOT NULL THEN 'REJECTED' ELSE 'PENDING' END;
ALTER TABLE ai_request_log ADD house_version BIGINT;
CREATE INDEX idx_user_review ON sys_user(account_status,store_id,id);
CREATE INDEX idx_owner_contact ON owner(store_id,broker_id,deleted,next_contact_at);
CREATE INDEX idx_customer_contact ON customer(store_id,broker_id,deleted,status,next_contact_at);
INSERT INTO backend_schema_version(version) VALUES(3);
