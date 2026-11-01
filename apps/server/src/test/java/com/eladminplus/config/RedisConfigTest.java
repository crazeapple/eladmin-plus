package com.eladminplus.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

/** 前置：本地 Redis 已起（just up） */
@SpringBootTest
class RedisConfigTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void key前缀集中拼装() {
        assertEquals("eladmin-plus:test:greeting", RedisConfig.key("test", "greeting"));
    }

    @Test
    void 读写回环且value为JSON序列化() {
        String key = RedisConfig.key("test", "roundtrip");
        Map<String, String> value = new LinkedHashMap<>();
        value.put("hello", "world");
        value.put("lang", "中文");

        redisTemplate.delete(key);
        redisTemplate.opsForValue().set(key, value);

        @SuppressWarnings("unchecked")
        Map<String, String> read = (Map<String, String>) redisTemplate.opsForValue().get(key);
        assertEquals(value, read);

        redisTemplate.delete(key);
    }
}
