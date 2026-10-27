-- 已导入旧库时执行：房源增加归属业主，按同编号回填演示数据
ALTER TABLE house
  ADD COLUMN owner_id bigint DEFAULT NULL COMMENT '归属业主ID' AFTER broker_id,
  ADD KEY idx_house_owner (owner_id);
UPDATE house h INNER JOIN owner o ON o.id=h.id AND o.deleted=0
  SET h.owner_id=o.id WHERE h.deleted=0 AND h.owner_id IS NULL;
