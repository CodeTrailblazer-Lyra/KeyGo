-- 激活码表新增字段
ALTER TABLE activation_code ADD COLUMN IF NOT EXISTS fetch_user VARCHAR(50);
ALTER TABLE activation_code ADD COLUMN IF NOT EXISTS remark VARCHAR(200);
CREATE INDEX IF NOT EXISTS idx_activation_code_fetch_user ON activation_code(fetch_user);

-- 操作日志表
CREATE TABLE IF NOT EXISTS operation_log (
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL,
    action     VARCHAR(32)  NOT NULL,
    target     VARCHAR(100),
    detail     VARCHAR(500),
    create_time TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_operation_log_create_time ON operation_log(create_time DESC);

-- 公告表
CREATE TABLE IF NOT EXISTS announcement (
    id         BIGSERIAL PRIMARY KEY,
    content    VARCHAR(500) NOT NULL,
    publisher  VARCHAR(50),
    pinned     BOOLEAN      NOT NULL DEFAULT false,
    create_time TIMESTAMP   NOT NULL DEFAULT NOW()
);
