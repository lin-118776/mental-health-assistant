package org.example.qispringboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.qispringboot.entity.EmotionDiary;

@Mapper
public interface EmotionDiaryMapper extends BaseMapper<EmotionDiary> {
}