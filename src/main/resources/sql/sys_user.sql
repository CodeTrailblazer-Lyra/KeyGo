-- =============================================================================
-- 用户表 sys_user
-- 幂等脚本：库中缺什么补什么，可重复执行
-- =============================================================================

CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGSERIAL       PRIMARY KEY,
    username    VARCHAR(50)     NOT NULL UNIQUE,
    password    VARCHAR(100)    NOT NULL,
    role        VARCHAR(20)     NOT NULL DEFAULT 'ROLE_USER',
    approved    BOOLEAN         NOT NULL DEFAULT false,
    create_time TIMESTAMP       NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  sys_user              IS '系统用户表';
COMMENT ON COLUMN sys_user.id           IS '主键（自增）';
COMMENT ON COLUMN sys_user.username     IS '用户名（唯一）';
COMMENT ON COLUMN sys_user.password     IS 'BCrypt 加密密码';
COMMENT ON COLUMN sys_user.role         IS '角色枚举：ROLE_SUPER_ADMIN / ROLE_ADMIN / ROLE_USER';
COMMENT ON COLUMN sys_user.approved     IS '是否通过管理员审核（新注册默认 false，审核通过后方可登录）';
COMMENT ON COLUMN sys_user.create_time  IS '创建时间（JPA @CreationTimestamp 自动填充）';
COMMENT ON COLUMN sys_user.update_time  IS '更新时间（JPA @UpdateTimestamp 自动填充）';

-- -----------------------------------------------------------------------------
-- 初始超级管理员 wx（按 username 幂等插入，不指定 id 以免打乱自增序列）
-- 密码为 BCrypt 加密后的预设值
-- -----------------------------------------------------------------------------
INSERT INTO sys_user (username, password, role, approved, create_time, update_time)
SELECT 'wx',
       '$2a$10$faEzz8sy/qQMO8pM3hu60u2cjUZ9NFMFW581AhEQIN2oajTeJnURu',
       'ROLE_SUPER_ADMIN', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'wx');
