-- 核销审计日志表（幂等脚本：缺什么补什么，可重复执行）
CREATE TABLE IF NOT EXISTS activation_log (
    id          BIGSERIAL PRIMARY KEY,
    code_hash   VARCHAR(64),
    device_id   VARCHAR(100),
    ip          VARCHAR(45),
    result      VARCHAR(32) NOT NULL,
    create_time TIMESTAMP   NOT NULL DEFAULT NOW()
);

COMMENT ON COLUMN activation_log.code_hash IS '被核销激活码的 SHA-256 哈希（不记明文）';
COMMENT ON COLUMN activation_log.device_id IS '发起核销的设备标识';
COMMENT ON COLUMN activation_log.ip IS '客户端 IP';
COMMENT ON COLUMN activation_log.result IS '核销结果：SUCCESS/INVALID/ALREADY_USED/EXPIRED';

CREATE INDEX IF NOT EXISTS idx_activation_log_create_time ON activation_log (create_time DESC);
