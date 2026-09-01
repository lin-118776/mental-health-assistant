package org.example.qispringboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.qispringboot.entity.KnowledgeCategory;

@Mapper
public interface KnowledgeCategoryMapper extends BaseMapper<KnowledgeCategory> {
}