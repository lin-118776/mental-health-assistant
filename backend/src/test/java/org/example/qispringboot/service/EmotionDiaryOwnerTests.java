package org.example.qispringboot.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.example.qispringboot.DTO.command.EmotionDiaryCreateDTO;
import org.example.qispringboot.entity.EmotionDiary;
import org.example.qispringboot.exception.BusinessException;
import org.example.qispringboot.mapper.EmotionDiaryMapper;
import org.example.qispringboot.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmotionDiaryOwnerTests {
    private EmotionDiaryService service;
    private EmotionDiaryMapper mapper;
    private UserMapper users;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), EmotionDiary.class);
        service = new EmotionDiaryService();
        mapper = mock(EmotionDiaryMapper.class);
        users = mock(UserMapper.class);
        ReflectionTestUtils.setField(service, "diaryMapper", mapper);
        ReflectionTestUtils.setField(service, "userMapper", users);
    }

    @Test
    void listIsScopedToOwnerAndKeepsPaginationAndStableOrder() {
        doAnswer(invocation -> {
            Page<EmotionDiary> page = invocation.getArgument(0);
            page.setTotal(12);
            page.setRecords(List.of(EmotionDiary.builder().id(11L).userId(7L).diaryContent("我的日志").build()));
            return page;
        }).when(mapper).selectPage(any(Page.class), any(Wrapper.class));
        var result = service.myPage(7L, 2, 10);
        assertEquals(2, result.getCurrent());
        assertEquals(12, result.getTotal());
        assertEquals("我的日志", result.getRecords().get(0).getDiaryContent());
        var query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(any(Page.class), query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("user_id"));
        assertTrue(query.getValue().getSqlSegment().contains("ORDER BY diary_date DESC,id DESC"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(7L));
        verifyNoInteractions(users);
    }

    @Test
    void adminDetailReadsCurrentRecordAndOnlyItsOwnerDisplayName() {
        when(mapper.selectById(11L)).thenReturn(EmotionDiary.builder().id(11L).userId(7L)
                .diaryContent("最新日志内容").aiEmotionAnalysis("{\"primaryEmotion\":\"平静\"}").build());
        when(users.selectById(7L)).thenReturn(org.example.qispringboot.entity.User.builder()
                .id(7L).username("owner").nickname("昵称").build());
        var detail = service.adminDetail(11L);
        assertEquals("最新日志内容", detail.getDiaryContent());
        assertEquals("昵称", detail.getNickname());
        assertEquals(7L, detail.getUserId());
        assertNotNull(detail.getAiEmotionAnalysis());
        verify(users).selectById(7L);
        verify(users, never()).selectList(any());
    }

    @Test
    void adminDetailHandlesDeletedDiaryAndMissingOwnerWithoutInventingAnalysis() {
        assertThrows(BusinessException.class, () -> service.adminDetail(99L));
        verifyNoInteractions(users);
        when(mapper.selectById(11L)).thenReturn(EmotionDiary.builder().id(11L).userId(7L).build());
        var detail = service.adminDetail(11L);
        assertEquals("用户 #7", detail.getNickname());
        assertNull(detail.getAiEmotionAnalysis());
    }

    @Test
    void boundsPagination() {
        service.myPage(7L, -2, 10000);
        var page = ArgumentCaptor.forClass(Page.class);
        verify(mapper).selectPage(page.capture(), any(Wrapper.class));
        assertEquals(1, page.getValue().getCurrent());
        assertEquals(100, page.getValue().getSize());
    }

    @Test
    void readsOwnDetailWithBothIdAndOwnerPredicates() {
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(EmotionDiary.builder().id(11L).userId(7L)
                .diaryDate(LocalDate.of(2026, 11, 11)).moodScore(6).diaryContent("今天不错").build());
        assertEquals("今天不错", service.myDetail(7L, 11L).getDiaryContent());
        var query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectOne(query.capture());
        assertOwner(query.getValue().getSqlSegment(), query.getValue().getParamNameValuePairs());
    }

    @Test
    void missingOrForeignDetailIsRejected() {
        assertThrows(BusinessException.class, () -> service.myDetail(7L, 11L));
    }

    @Test
    void updateIsAtomicOwnerScopedAndCanClearOptionalFieldsAndOldAnalysis() {
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        EmotionDiaryCreateDTO dto = new EmotionDiaryCreateDTO();
        dto.setDiaryDate(LocalDate.of(2026, 11, 10));
        dto.setMoodScore(8);
        dto.setDiaryContent("修改后");
        service.updateMine(7L, 11L, dto);
        var update = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(mapper).update(isNull(), update.capture());
        var wrapper = update.getValue();
        assertOwner(wrapper.getSqlSegment(), wrapper.getParamNameValuePairs());
        String set = wrapper.getSqlSet();
        assertTrue(set.contains("sleep_quality="));
        assertTrue(set.contains("stress_level="));
        assertTrue(set.contains("ai_emotion_analysis="));
        assertTrue(set.contains("ai_analysis_updated_at="));
        assertFalse(set.contains("user_id="));
        assertFalse(set.contains("created_at="));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("修改后"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(null));
    }

    @Test
    void foreignOrMissingUpdateIsRejected() {
        assertThrows(BusinessException.class, () -> service.updateMine(7L, 11L, new EmotionDiaryCreateDTO()));
        verify(mapper, never()).updateById(any(EmotionDiary.class));
    }

    @Test
    void deletesOnlyMatchingOwnerAndDiary() {
        when(mapper.delete(any(Wrapper.class))).thenReturn(1);
        service.deleteMine(7L, 11L);
        var query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).delete(query.capture());
        assertOwner(query.getValue().getSqlSegment(), query.getValue().getParamNameValuePairs());
        verify(mapper, never()).deleteById(anyLong());
    }

    @Test
    void foreignOrMissingDeleteIsRejected() {
        assertThrows(BusinessException.class, () -> service.deleteMine(7L, 11L));
    }

    private void assertOwner(String sql, Map<?, ?> values) {
        assertTrue(sql.contains("id ="));
        assertTrue(sql.contains("user_id ="));
        assertTrue(values.containsValue(11L));
        assertTrue(values.containsValue(7L));
    }
}
