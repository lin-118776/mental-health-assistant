-- ============================================================
-- 心理咨询会话表 chat_session
-- 一个用户可拥有多个咨询会话，每条消息挂在对应会话下
-- ============================================================
CREATE TABLE IF NOT EXISTS `chat_session` (
    `id`                       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    `user_id`                  BIGINT       NOT NULL COMMENT '所属用户ID',
    `session_title`            VARCHAR(200) DEFAULT NULL COMMENT '会话标题',
    `last_emotion_analysis`    TEXT         DEFAULT NULL COMMENT '最后一次情绪分析结果(JSON格式)',
    `last_emotion_updated_at`  DATETIME     DEFAULT NULL COMMENT '最后一次情绪分析更新时间',
    `created_at`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`) COMMENT '按用户查询会话'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='心理咨询会话表';

-- ============================================================
-- 心理咨询聊天消息表 chat_message
-- sender_type: 1=用户 2=AI助手；message_type: 1=文本
-- ============================================================
CREATE TABLE IF NOT EXISTS `chat_message` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `session_id`   BIGINT      NOT NULL COMMENT '所属会话ID',
    `sender_type`  TINYINT     NOT NULL COMMENT '发送者类型 1:用户 2:AI助手',
    `message_type` TINYINT     NOT NULL DEFAULT 1 COMMENT '消息类型 1:文本',
    `content`      TEXT        NOT NULL COMMENT '消息内容',
    `emotion_tag`  VARCHAR(50) DEFAULT NULL COMMENT '情绪标签',
    `ai_model`     VARCHAR(50) DEFAULT NULL COMMENT '使用的AI模型',
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`) COMMENT '按会话查询消息'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='心理咨询聊天消息表';