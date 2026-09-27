-- 激活码表（幂等脚本：库中缺什么补什么，可重复执行）
CREATE TABLE IF NOT EXISTS activation_code (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(48) UNIQUE,
    used BOOLEAN NOT NULL DEFAULT false,
    fetch_time TIMESTAMP NULL,
    fetch_user VARCHAR(50),
    remark VARCHAR(200),
    code_hash VARCHAR(64),
    device_id VARCHAR(100),
    bind_time TIMESTAMP NULL,
    expire_time TIMESTAMP NULL,
    create_time TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 老库已有本表但缺后增列时自动补齐
ALTER TABLE activation_code ADD COLUMN IF NOT EXISTS fetch_user VARCHAR(50);
ALTER TABLE activation_code ADD COLUMN IF NOT EXISTS remark VARCHAR(200);
-- 核销改造：只存哈希的新码 + 设备绑定 + 过期时间
ALTER TABLE activation_code ADD COLUMN IF NOT EXISTS code_hash VARCHAR(64);
ALTER TABLE activation_code ADD COLUMN IF NOT EXISTS device_id VARCHAR(100);
ALTER TABLE activation_code ADD COLUMN IF NOT EXISTS bind_time TIMESTAMP;
ALTER TABLE activation_code ADD COLUMN IF NOT EXISTS expire_time TIMESTAMP;
-- 新生成的码不再写入明文，code 放开 NOT NULL（存量数据保留明文继续可核销）
ALTER TABLE activation_code ALTER COLUMN code DROP NOT NULL;

-- 单独添加注释（可选，不加也不影响业务）
COMMENT ON COLUMN activation_code.code IS '激活码明文（存量保留，新码为 NULL）';
COMMENT ON COLUMN activation_code.used IS 'false=未使用 true=已使用/已核销';
COMMENT ON COLUMN activation_code.fetch_time IS '被领取时间';
COMMENT ON COLUMN activation_code.fetch_user IS '获取人用户名';
COMMENT ON COLUMN activation_code.remark IS '备注（来源/批次/用途）';
COMMENT ON COLUMN activation_code.code_hash IS '明文 SHA-256 十六进制，核销主匹配键';
COMMENT ON COLUMN activation_code.device_id IS '核销绑定的设备标识';
COMMENT ON COLUMN activation_code.bind_time IS '核销绑定时间';
COMMENT ON COLUMN activation_code.expire_time IS '过期时间（NULL=永不过期）';
COMMENT ON COLUMN activation_code.create_time IS '创建时间';

-- code 上不再单独建索引：UNIQUE 约束已自动创建等价索引
CREATE UNIQUE INDEX IF NOT EXISTS uk_code_hash ON activation_code (code_hash);
CREATE INDEX IF NOT EXISTS idx_activation_code_used ON activation_code (used);
CREATE INDEX IF NOT EXISTS idx_activation_code_fetch_user ON activation_code (fetch_user);
CREATE INDEX IF NOT EXISTS idx_activation_code_fetch_time ON activation_code (fetch_time DESC);
CREATE INDEX IF NOT EXISTS idx_activation_code_used_fetch_time ON activation_code (used, fetch_time DESC);

-- 可选：为存量明文回填哈希，回填后核销不再走明文降级路径（需 pgcrypto 扩展）。
-- 注意：仅当明文与生成时口径一致（trim+大写）才可比对；执行前务必备份。
-- CREATE EXTENSION IF NOT EXISTS pgcrypto;
-- UPDATE activation_code
--    SET code_hash = encode(digest(code, 'sha256'), 'hex')
--  WHERE code IS NOT NULL AND code_hash IS NULL;
