CREATE TABLE sys_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER',
    approved BOOLEAN DEFAULT false,
    enabled BOOLEAN DEFAULT true,
    create_time TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_sys_user_username ON sys_user(username);

-- wx 为最高管理员（超级管理员），已预审核通过
INSERT INTO public.sys_user (id, username, password, role, approved, enabled, create_time, update_time)
VALUES (1, 'wx', '$2a$10$faEzz8sy/qQMO8pM3hu60u2cjUZ9NFMFW581AhEQIN2oajTeJnURu',
        'ROLE_SUPER_ADMIN', true, true, DEFAULT, DEFAULT);
