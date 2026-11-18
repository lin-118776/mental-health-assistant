package org.example.qispringboot.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.qispringboot.entity.KnowledgeCategory;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.*;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class CategoryCacheTests {
    @Test void cacheMissWritesWithTtlHitAvoidsDatabaseAndEvictClearsKey() {
        var redis=mock(StringRedisTemplate.class);ValueOperations<String,String> values=mock(ValueOperations.class);when(redis.opsForValue()).thenReturn(values);
        var cache=new CategoryCache(redis,new ObjectMapper(),true);
        var rows=List.of(KnowledgeCategory.builder().id(1L).categoryName("睡眠").build());
        assertEquals(rows,cache.get(()->rows));
        verify(values).set(eq(CategoryCache.KEY),contains("睡眠"),eq(Duration.ofMinutes(5)));
        when(values.get(CategoryCache.KEY)).thenReturn("[{\"id\":1,\"categoryName\":\"睡眠\"}]");
        assertEquals("睡眠",cache.get(()->{throw new AssertionError("must use cache");}).get(0).getCategoryName());
        cache.evict();verify(redis).delete(CategoryCache.KEY);
    }
    @Test void redisFailureFallsBackAndDisabledCacheMakesNoRedisCalls() {
        var redis=mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("redis down"));
        var rows=List.of(KnowledgeCategory.builder().id(1L).build());
        assertEquals(rows,new CategoryCache(redis,new ObjectMapper(),true).get(()->rows));
        reset(redis);
        assertEquals(rows,new CategoryCache(redis,new ObjectMapper(),false).get(()->rows));
        verifyNoInteractions(redis);
    }
}
