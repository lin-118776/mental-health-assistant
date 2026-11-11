package org.example.qispringboot.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.qispringboot.DTO.response.AdminChatSessionResponseDTO;

@Mapper
public interface AdminChatMapper {
    // 只查询当前分页的摘要，不把全部用户的完整聊天内容加载到内存。
    String SUMMARY_SQL = """
            SELECT s.id, s.user_id,
              COALESCE(NULLIF(u.nickname, ''), u.username, CONCAT('用户 #', s.user_id)) AS user_nickname,
              u.avatar AS user_avatar, s.session_title,
              (SELECT COUNT(*) FROM chat_message m WHERE m.session_id = s.id) AS message_count,
              (SELECT LEFT(m.content, 200) FROM chat_message m WHERE m.session_id = s.id ORDER BY m.id DESC LIMIT 1) AS last_message_content,
              COALESCE((SELECT m.created_at FROM chat_message m WHERE m.session_id = s.id ORDER BY m.id DESC LIMIT 1), s.created_at) AS last_message_time
            FROM chat_session s LEFT JOIN `user` u ON u.id = s.user_id
            """;

    @Select(SUMMARY_SQL + " ORDER BY s.created_at DESC, s.id DESC")
    Page<AdminChatSessionResponseDTO> selectSessions(Page<AdminChatSessionResponseDTO> page);

    @Select(SUMMARY_SQL + " WHERE s.id = #{id}")
    AdminChatSessionResponseDTO selectSession(@Param("id") Long id);
}
