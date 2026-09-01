package org.example.qispringboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 心理咨询会话实体类
 * 对应数据表 chat_session
 */
@Data
@TableName("chat_session")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {

    // 会话ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 所属用户ID
    @TableField("user_id")
    private Long userId;

    // 会话标题
    @TableField("session_title")
    private String sessionTitle;

    // 最后一次情绪分析结果（JSON格式）
    @TableField("last_emotion_analysis")
    private String lastEmotionAnalysis;

    // 最后一次情绪分析更新时间
    @TableField("last_emotion_updated_at")
    private LocalDateTime lastEmotionUpdatedAt;

    // 创建时间
    @TableField("created_at")
    private LocalDateTime createdAt;

    // 更新时间
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}