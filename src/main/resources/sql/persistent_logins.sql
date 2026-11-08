-- =============================================================================
-- Remember-Me 持久化登录表 persistent_logins
-- Spring Security PersistentTokenRepository 使用
-- 注意：该表不对应 JPA 实体，Hibernate 不会自动创建，需手动执行本 SQL
-- =============================================================================

CREATE TABLE IF NOT EXISTS persistent_logins (
    username  VARCHAR(64)   NOT NULL,
    series    VARCHAR(64)   PRIMARY KEY,
    token     VARCHAR(64)   NOT NULL,
    last_used TIMESTAMP     NOT NULL
);

COMMENT ON TABLE  persistent_logins             IS 'Remember-Me 持久化登录令牌表（Spring Security 使用）';
COMMENT ON COLUMN persistent_logins.username    IS '用户名';
COMMENT ON COLUMN persistent_logins.series      IS '令牌序列号（主键）';
COMMENT ON COLUMN persistent_logins.token       IS '令牌值';
COMMENT ON COLUMN persistent_logins.last_used   IS '最后使用时间';

-- -----------------------------------------------------------------------------
-- 索引
-- -----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_persistent_logins_username
    ON persistent_logins (username);
