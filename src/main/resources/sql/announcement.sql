-- 公告表（幂等脚本：库中缺什么补什么，可重复执行）
CREATE TABLE IF NOT EXISTS announcement (
    id          BIGSERIAL PRIMARY KEY,
    content     VARCHAR(500) NOT NULL,
    publisher   VARCHAR(50),
    pinned      BOOLEAN      NOT NULL DEFAULT false,
    visible     BOOLEAN      NOT NULL DEFAULT true,
    create_time TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 老库已有本表但缺后增列时自动补齐（false=暂不上线，获取页不显示）
ALTER TABLE announcement ADD COLUMN IF NOT EXISTS visible BOOLEAN NOT NULL DEFAULT true;
