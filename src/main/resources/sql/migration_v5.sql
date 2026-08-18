-- 公告表新增可见性字段（false=暂不上线，获取页不显示）
ALTER TABLE announcement ADD COLUMN IF NOT EXISTS visible BOOLEAN NOT NULL DEFAULT true;
