package org.example.qispringboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.qispringboot.entity.ChatSession;

/**
 * 心理咨询会话Mapper
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}