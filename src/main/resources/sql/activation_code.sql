-- =============================================================================
-- 激活码表 activation_code
-- 幂等脚本：库中缺什么补什么，可重复执行
-- 存储明文激活码，核销时直接匹配
-- =============================================================================

CREATE TABLE IF NOT EXISTS activation_code (
    id                   BIGSERIAL       PRIMARY KEY,
    code                 VARCHAR(48)     NOT NULL UNIQUE,
    used                 BOOLEAN         NOT NULL DEFAULT false,
    fetch_time           TIMESTAMP,
    fetch_user           VARCHAR(50),
    device_id            VARCHAR(100),
    expire_time          TIMESTAMP,
    remark               VARCHAR(200),
    official_use_status  INTEGER,
    official_use_time    TIMESTAMP,
    official_product     VARCHAR(100),
    official_device      VARCHAR(200),
    official_code        INTEGER,
    official_checked_at  TIMESTAMP,
    create_time          TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- 老库升级：官方联网验证字段（新库上面已建全，此处为已存在的旧表补列，幂等）
-- -----------------------------------------------------------------------------
ALTER TABLE activation_code ADD COLUMN IF NOT EXISTS official_use_status INTEGER;
ALTER TABLE activation_code ADD COLUMN IF NOT EXISTS official_use_time   TIMESTAMP;
ALTER TABLE activation_code ADD COLUMN IF NOT EXISTS official_product    VARCHAR(100);
ALTER TABLE activation_code ADD COLUMN IF NOT EXISTS official_device     VARCHAR(200);
ALTER TABLE activation_code ADD COLUMN IF NOT EXISTS official_code       INTEGER;
ALTER TABLE activation_code ADD COLUMN IF NOT EXISTS official_checked_at TIMESTAMP;

COMMENT ON TABLE  activation_code               IS '激活码表（存储明文激活码）';
COMMENT ON COLUMN activation_code.id             IS '主键（自增）';
COMMENT ON COLUMN activation_code.code           IS '激活码明文（唯一，最长 48 字符）';
COMMENT ON COLUMN activation_code.used           IS '使用状态：false=未使用，true=已使用/已核销';
COMMENT ON COLUMN activation_code.fetch_time     IS '被领取/核销时间';
COMMENT ON COLUMN activation_code.fetch_user     IS '获取人用户名（未获取时为 NULL）';
COMMENT ON COLUMN activation_code.device_id      IS '核销绑定的设备标识（未核销时为 NULL）';
COMMENT ON COLUMN activation_code.expire_time    IS '过期时间（NULL=永不过期）';
COMMENT ON COLUMN activation_code.remark         IS '备注（来源/批次/用途，最长 200 字符）';
COMMENT ON COLUMN activation_code.official_use_status IS '官方接口 use_status（1=已使用）；NULL=从未验证';
COMMENT ON COLUMN activation_code.official_use_time   IS '官方记录的激活/使用时间';
COMMENT ON COLUMN activation_code.official_product    IS '官方产品名称（name_cn）';
COMMENT ON COLUMN activation_code.official_device     IS '官方记录的激活设备（manu_name + manu_device_name）';
COMMENT ON COLUMN activation_code.official_code       IS '官方接口顶层 code（0=查询成功，非 0=码无效或官方侧异常）';
COMMENT ON COLUMN activation_code.official_checked_at IS '本地最后一次成功向官方发起验证的时间';
COMMENT ON COLUMN activation_code.create_time    IS '创建时间（JPA @CreationTimestamp 自动填充）';

-- -----------------------------------------------------------------------------
-- 索引
-- -----------------------------------------------------------------------------
-- code 唯一索引通过 UNIQUE 约束自动创建

CREATE INDEX IF NOT EXISTS idx_activation_code_used
    ON activation_code (used);

CREATE INDEX IF NOT EXISTS idx_activation_code_fetch_user
    ON activation_code (fetch_user);

CREATE INDEX IF NOT EXISTS idx_activation_code_fetch_time
    ON activation_code (fetch_time DESC);

-- 复合索引：管理端列表「未使用在前按 ID 升序，已使用在后按 fetch_time 倒序」
CREATE INDEX IF NOT EXISTS idx_activation_code_used_fetch_time
    ON activation_code (used, fetch_time DESC);
