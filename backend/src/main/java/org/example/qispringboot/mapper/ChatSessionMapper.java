package org.example.qispringboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.qispringboot.DTO.response.ChatSessionSummaryResponseDTO;
import org.example.qispringboot.entity.ChatSession;

/**
 * 心理咨询会话Mapper
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
    // 只汇总当前用户分页内的会话，预览最多200字，不加载完整历史消息。
    @Select("""
            SELECT s.id, s.user_id, s.session_title, s.created_at, s.updated_at,
              (SELECT COUNT(*) FROM chat_message m WHERE m.session_id = s.id) AS message_count,
              (SELECT LEFT(m.content, 200) FROM chat_message m WHERE m.session_id = s.id ORDER BY m.id DESC LIMIT 1) AS last_message_content,
              (SELECT m.created_at FROM chat_message m WHERE m.session_id = s.id ORDER BY m.id DESC LIMIT 1) AS last_message_time,
              COALESCE((SELECT TIMESTAMPDIFF(MINUTE, MIN(m.created_at), MAX(m.created_at)) FROM chat_message m WHERE m.session_id = s.id), 0) AS duration_minutes
            FROM chat_session s WHERE s.user_id = #{userId}
            ORDER BY s.created_at DESC, s.id DESC
            """)
    Page<ChatSessionSummaryResponseDTO> selectOwnedSummaries(Page<ChatSessionSummaryResponseDTO> page,
            @Param("userId") Long userId);
}
