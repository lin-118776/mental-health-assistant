-- 在schema.sql之后执行。只补不存在的基础分类，不覆盖名称、状态或顺序。
USE mental_health_assistant;
SET NAMES utf8mb4;

INSERT INTO knowledge_category (parent_id, category_name, category_code, description, sort_order, status)
SELECT 0, '情绪管理', 'emotion', '日常情绪记录与情绪管理知识', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM knowledge_category WHERE category_code = 'emotion');

INSERT INTO knowledge_category (parent_id, category_name, category_code, description, sort_order, status)
SELECT 0, '压力调适', 'stress', '学习、生活与人际压力相关知识', 2, 1
WHERE NOT EXISTS (SELECT 1 FROM knowledge_category WHERE category_code = 'stress');

INSERT INTO knowledge_category (parent_id, category_name, category_code, description, sort_order, status)
SELECT 0, '睡眠与生活', 'sleep-life', '睡眠习惯与日常生活相关知识', 3, 1
WHERE NOT EXISTS (SELECT 1 FROM knowledge_category WHERE category_code = 'sleep-life');
