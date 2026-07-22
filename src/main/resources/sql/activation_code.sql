CREATE TABLE activation_code (
                                 id BIGSERIAL PRIMARY KEY,
                                 code VARCHAR(48) NOT NULL UNIQUE,
                                 used BOOLEAN NOT NULL DEFAULT false,
                                 fetch_time TIMESTAMP NULL,
                                 create_time TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 单独添加注释（可选，不加也不影响业务）
COMMENT ON COLUMN activation_code.code IS '激活码字符串';
COMMENT ON COLUMN activation_code.used IS 'false=未使用 true=已使用';
COMMENT ON COLUMN activation_code.fetch_time IS '被领取时间';
COMMENT ON COLUMN activation_code.create_time IS '创建时间';

CREATE INDEX idx_activation_code_code ON activation_code(code);
CREATE INDEX idx_activation_code_used ON activation_code(used);