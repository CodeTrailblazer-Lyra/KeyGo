-- 数据库索引优化（统一收敛到本脚本，全部 IF NOT EXISTS，可重复执行）
-- 说明：激活码表无 deleted 软删除列，状态以 used 列表达，故 (used, deleted) 需求以
--       (used, fetch_time DESC) 复合索引覆盖实际查询模式（未使用在前/已使用按获取时间倒序）。

-- 激活码表：复合索引（状态 + 获取时间倒序），支撑分页列表默认排序
CREATE INDEX IF NOT EXISTS idx_activation_code_used_fetch_time
    ON activation_code (used, fetch_time DESC);

-- 激活码表：获取时间倒序索引，支撑已使用列表排序 / 我的记录排序
CREATE INDEX IF NOT EXISTS idx_activation_code_fetch_time
    ON activation_code (fetch_time DESC);

-- 激活码表：获取人索引，支撑「我的获取记录」按用户查询
CREATE INDEX IF NOT EXISTS idx_activation_code_fetch_user
    ON activation_code (fetch_user);

-- 操作日志表：创建时间倒序索引，支撑日志分页倒序查询
CREATE INDEX IF NOT EXISTS idx_operation_log_create_time
    ON operation_log (create_time DESC);
