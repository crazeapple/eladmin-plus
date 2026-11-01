package com.eladminplus.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 模板约定：key 字符串序列化、value JSON 序列化；
 * key 一律带 eladmin-plus:&lt;域&gt;:&lt;键&gt; 前缀，经 {@link #key} 集中拼装。
 */
@Configuration
public class RedisConfig {

    public static final String KEY_PREFIX = "eladmin-plus:";

    /** 按域拼装完整 key：eladmin-plus:&lt;域&gt;:&lt;键&gt; */
    public static String key(String domain, String key) {
        return KEY_PREFIX + domain + ":" + key;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        // 无参构造自带 @class 类型信息（写入携带、读取还原）；依赖显式引入的 jackson-databind
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer();

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
