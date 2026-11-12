-- 新建环境使用。不会删除数据；IF NOT EXISTS 不会修复已有表的字段差异。
CREATE DATABASE IF NOT EXISTS mental_health_assistant CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mental_health_assistant;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL,
    `email` VARCHAR(100) DEFAULT NULL,
    `phone` VARCHAR(20) DEFAULT NULL,
    `password` VARCHAR(255) NOT NULL COMMENT 'BCrypt密码哈希',
    `nickname` VARCHAR(50) DEFAULT NULL,
    `avatar` VARCHAR(255) DEFAULT NULL,
    `gender` TINYINT DEFAULT 0 COMMENT '0未知 1男 2女',
    `birthday` DATE DEFAULT NULL,
    `user_type` TINYINT NOT NULL DEFAULT 1 COMMENT '1普通用户 2管理员',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    UNIQUE KEY `uk_user_phone` (`phone`),
    KEY `idx_user_email` (`email`),
    KEY `idx_user_created_at` (`created_at`),
    KEY `idx_user_status_created` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `emotion_diary` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `diary_date` DATE NOT NULL,
    `mood_score` INT NOT NULL,
    `dominant_emotion` VARCHAR(50) DEFAULT NULL,
    `emotion_triggers` TEXT DEFAULT NULL,
    `diary_content` TEXT DEFAULT NULL,
    `sleep_quality` INT DEFAULT NULL,
    `stress_level` INT DEFAULT NULL,
    `ai_emotion_analysis` TEXT DEFAULT NULL,
    `ai_analysis_updated_at` DATETIME(6) DEFAULT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    KEY `idx_diary_user_date` (`user_id`, `diary_date`),
    KEY `idx_diary_date` (`diary_date`),
    KEY `idx_diary_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `knowledge_category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `parent_id` BIGINT DEFAULT 0,
    `category_name` VARCHAR(100) NOT NULL,
    `category_code` VARCHAR(100) DEFAULT NULL,
    `description` TEXT DEFAULT NULL,
    `sort_order` INT NOT NULL DEFAULT 0,
    `status` TINYINT NOT NULL DEFAULT 1,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category_code` (`category_code`),
    KEY `idx_category_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `knowledge_article` (
    `id` VARCHAR(32) NOT NULL COMMENT 'MyBatis-Plus生成的UUID',
    `category_id` BIGINT NOT NULL,
    `title` VARCHAR(255) NOT NULL,
    `summary` TEXT DEFAULT NULL,
    `content` LONGTEXT DEFAULT NULL,
    `cover_image` VARCHAR(500) DEFAULT NULL,
    `tags` TEXT DEFAULT NULL,
    `author_id` BIGINT DEFAULT NULL,
    `read_count` INT NOT NULL DEFAULT 0,
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0草稿 1发布 2下线',
    `published_at` DATETIME(6) DEFAULT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    KEY `idx_article_status_published` (`status`, `published_at`),
    KEY `idx_article_category_status` (`category_id`, `status`),
    KEY `idx_article_author` (`author_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_file_info` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `original_name` VARCHAR(255) NOT NULL,
    `file_path` VARCHAR(500) NOT NULL,
    `file_size` BIGINT NOT NULL DEFAULT 0,
    `file_type` VARCHAR(100) DEFAULT NULL,
    `business_type` VARCHAR(100) DEFAULT NULL,
    `business_id` VARCHAR(100) DEFAULT NULL,
    `business_field` VARCHAR(100) DEFAULT NULL,
    `upload_user_id` BIGINT DEFAULT NULL,
    `is_temp` TINYINT NOT NULL DEFAULT 0,
    `status` TINYINT NOT NULL DEFAULT 1,
    `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `expire_time` DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_file_business` (`business_type`, `business_id`),
    KEY `idx_file_uploader` (`upload_user_id`),
    KEY `idx_file_temp_expire` (`is_temp`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `chat_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `session_title` VARCHAR(200) DEFAULT NULL,
    `last_emotion_analysis` TEXT DEFAULT NULL,
    `last_emotion_updated_at` DATETIME(6) DEFAULT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    KEY `idx_session_user_created` (`user_id`, `created_at`),
    KEY `idx_session_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `chat_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `session_id` BIGINT NOT NULL,
    `sender_type` TINYINT NOT NULL COMMENT '1用户 2AI',
    `message_type` TINYINT NOT NULL DEFAULT 1,
    `content` TEXT NOT NULL,
    `emotion_tag` VARCHAR(50) DEFAULT NULL,
    `ai_model` VARCHAR(50) DEFAULT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    KEY `idx_message_session_id` (`session_id`, `id`),
    KEY `idx_message_sender_created` (`sender_type`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
