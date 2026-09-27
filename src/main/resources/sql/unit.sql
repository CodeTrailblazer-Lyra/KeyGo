-- 使用单位表（幂等脚本：库中缺什么补什么，可重复执行）
-- 管理员维护，申领激活码时可选写入备注
CREATE TABLE IF NOT EXISTS unit (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_unit_name UNIQUE (name)
);
