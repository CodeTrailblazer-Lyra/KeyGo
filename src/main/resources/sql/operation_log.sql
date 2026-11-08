-- =============================================================================
-- 操作日志表 operation_log
-- 幂等脚本：库中缺什么补什么，可重复执行
-- =============================================================================

CREATE TABLE IF NOT EXISTS operation_log (
    id          BIGSERIAL       PRIMARY KEY,
    username    VARCHAR(50)     NOT NULL,
    action      VARCHAR(32)     NOT NULL,
    target      VARCHAR(100),
    detail      VARCHAR(500),
    create_time TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  operation_log                 IS '操作日志表（记录关键业务操作，用于安全审计）';
COMMENT ON COLUMN operation_log.id              IS '主键（自增）';
COMMENT ON COLUMN operation_log.username        IS '操作人用户名';
COMMENT ON COLUMN operation_log.action          IS '操作类型：CLAIM / BATCH_CLAIM / MARK_USED / MARK_UNUSED / DELETE / IMPORT / BATCH_MARK_USED / BATCH_MARK_UNUSED / BATCH_DELETE / APPROVE / REJECT 等';
COMMENT ON COLUMN operation_log.target          IS '操作对象（激活码明文、用户 ID 等结构化标识）';
COMMENT ON COLUMN operation_log.detail          IS '操作详情（补充说明）';
COMMENT ON COLUMN operation_log.create_time     IS '创建时间（JPA @CreationTimestamp 自动填充）';

-- -----------------------------------------------------------------------------
-- 索引
-- -----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_operation_log_create_time
    ON operation_log (create_time DESC);
