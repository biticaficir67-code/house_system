-- 已导入旧库时执行：业主增加停用状态，原先逻辑删除的业主改回停用并保留档案
ALTER TABLE owner
  ADD COLUMN status varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '业主状态 ACTIVE在用 INACTIVE停用' AFTER deleted;
UPDATE owner SET deleted=0, status='INACTIVE' WHERE deleted=1;
UPDATE owner SET status='ACTIVE' WHERE deleted=0 AND status<>'INACTIVE';
