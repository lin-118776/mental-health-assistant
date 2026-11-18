package org.example.qispringboot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.qispringboot.entity.KnowledgeCategory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

@Service
public class CategoryCache {
    static final String KEY="mental:knowledge:categories:v1";
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final boolean enabled;
    public CategoryCache(StringRedisTemplate redis,ObjectMapper mapper,@Value("${app.cache.enabled:false}") boolean enabled) {this.redis=redis;this.mapper=mapper;this.enabled=enabled;}
    public List<KnowledgeCategory> get(Supplier<List<KnowledgeCategory>> loader) {
        if(!enabled) return loader.get();
        try {
            String value=redis.opsForValue().get(KEY);
            if(value!=null) return mapper.readValue(value,new TypeReference<>(){});
        } catch(Exception ignored) { /* 缓存故障不阻断数据库读取。 */ }
        var result=loader.get();
        try {redis.opsForValue().set(KEY,mapper.writeValueAsString(result),Duration.ofMinutes(5));}
        catch(Exception ignored) { /* 降级到数据库。 */ }
        return result;
    }
    public void evict() {if(enabled) redis.delete(KEY);}
}
