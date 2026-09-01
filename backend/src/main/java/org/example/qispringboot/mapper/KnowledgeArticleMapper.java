package org.example.qispringboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.qispringboot.entity.KnowledgeArticle;

@Mapper
public interface KnowledgeArticleMapper extends BaseMapper<KnowledgeArticle> {
}