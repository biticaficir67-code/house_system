-- 已导入旧库时执行：原先逻辑删除的客户改回停用并保留档案
UPDATE customer SET deleted=0, status='INACTIVE' WHERE deleted=1;
