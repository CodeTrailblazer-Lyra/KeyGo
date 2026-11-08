-- =============================================================================
-- 核销审计日志表 activation_log
-- 幂等脚本：库中缺什么补什么，可重复执行
-- =============================================================================

CREATE TABLE IF NOT EXISTS activation_log (
    id          BIGSERIAL       PRIMARY KEY,
    code        VARCHAR(200),
    device_id   VARCHAR(100),
    ip          VARCHAR(45),
    result      VARCHAR(32)     NOT NULL,
    create_time TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  activation_log                IS '核销审计日志表（异步写入，记录核销输入的激活码明文）';
COMMENT ON COLUMN activation_log.id             IS '主键（自增）';
COMMENT ON COLUMN activation_log.code           IS '被核销的激活码明文（无法定位时为输入原始值，仅排查用）';
COMMENT ON COLUMN activation_log.device_id      IS '发起核销的设备标识';
COMMENT ON COLUMN activation_log.ip             IS '客户端 IP（IPv6 最长 45 字符）';
COMMENT ON COLUMN activation_log.result         IS '核销结果：SUCCESS / INVALID / ALREADY_USED / EXPIRED';
COMMENT ON COLUMN activation_log.create_time    IS '创建时间（JPA @CreationTimestamp 自动填充）';

-- -----------------------------------------------------------------------------
-- 索引
-- -----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_activation_log_create_time
    ON activation_log (create_time DESC);
