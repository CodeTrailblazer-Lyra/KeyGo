-- Remember-Me 数据库持久化表（Spring Security PersistentTokenRepository 使用）
-- 注意：该表不是 JPA 实体，Hibernate 不会自动创建，需手动执行本 SQL
CREATE TABLE IF NOT EXISTS persistent_logins (
    username  VARCHAR(64) NOT NULL,
    series    VARCHAR(64) PRIMARY KEY,
    token     VARCHAR(64) NOT NULL,
    last_used TIMESTAMP   NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_persistent_logins_username ON persistent_logins(username);
