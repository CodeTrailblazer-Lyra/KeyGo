-- 用户表（幂等脚本：库中缺什么补什么，可重复执行）
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER',
    approved BOOLEAN DEFAULT false,
    enabled BOOLEAN DEFAULT true,
    create_time TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP NOT NULL DEFAULT NOW()
);

-- wx 为最高管理员（超级管理员），已预审核通过；按 username 幂等，不指定 id 以免打乱自增序列
-- 时间显式写 NOW()：旧库缺省 DEFAULT 未生效时也能插入（CREATE TABLE IF NOT EXISTS 不会补默认值）
INSERT INTO sys_user (username, password, role, approved, enabled, create_time, update_time)
SELECT 'wx', '$2a$10$faEzz8sy/qQMO8pM3hu60u2cjUZ9NFMFW581AhEQIN2oajTeJnURu',
       'ROLE_SUPER_ADMIN', true, true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'wx');
