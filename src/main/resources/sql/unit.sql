-- =============================================================================
-- 使用单位表 unit
-- 幂等脚本：库中缺什么补什么，可重复执行
-- =============================================================================

CREATE TABLE IF NOT EXISTS unit (
    id          BIGSERIAL       PRIMARY KEY,
    name        VARCHAR(100)    NOT NULL UNIQUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  unit                  IS '使用单位表（管理员维护，申领激活码时可选写入备注）';
COMMENT ON COLUMN unit.id               IS '主键（自增）';
COMMENT ON COLUMN unit.name             IS '单位名称（唯一）';
COMMENT ON COLUMN unit.created_at       IS '创建时间（JPA @CreationTimestamp 自动填充）';
