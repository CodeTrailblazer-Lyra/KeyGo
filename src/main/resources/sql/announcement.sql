-- =============================================================================
-- 系统公告表 announcement
-- 幂等脚本：库中缺什么补什么，可重复执行
-- =============================================================================

CREATE TABLE IF NOT EXISTS announcement (
    id          BIGSERIAL       PRIMARY KEY,
    content     VARCHAR(500)    NOT NULL,
    publisher   VARCHAR(50),
    pinned      BOOLEAN         NOT NULL DEFAULT false,
    visible     BOOLEAN         NOT NULL DEFAULT true,
    create_time TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  announcement                  IS '系统公告表（管理员发布，所有登录用户在获取页查看）';
COMMENT ON COLUMN announcement.id               IS '主键（自增）';
COMMENT ON COLUMN announcement.content          IS '公告内容（最长 500 字符）';
COMMENT ON COLUMN announcement.publisher        IS '发布人用户名';
COMMENT ON COLUMN announcement.pinned           IS '是否置顶';
COMMENT ON COLUMN announcement.visible          IS '是否在获取页显示（false=暂不上线）';
COMMENT ON COLUMN announcement.create_time      IS '创建时间（JPA @CreationTimestamp 自动填充）';
