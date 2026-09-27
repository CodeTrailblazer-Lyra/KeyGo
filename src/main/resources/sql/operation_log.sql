-- 操作日志表（幂等脚本：库中缺什么补什么，可重复执行）
CREATE TABLE IF NOT EXISTS operation_log (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL,
    action      VARCHAR(32)  NOT NULL,
    target      VARCHAR(100),
    detail      VARCHAR(500),
    create_time TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 日志列表按时间倒序分页
CREATE INDEX IF NOT EXISTS idx_operation_log_create_time ON operation_log (create_time DESC);
