package com.mete.ecommerce.product.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    /**
     * Raw byte[] template — hem JSON hem Protobuf aynı template'i kullanır.
     * Protobuf: toByteArray() / parseFrom()
     * JSON    : ObjectMapper.writeValueAsBytes() / readValue()
     *
     * JDK serialization kullanılmıyor: binary uyumsuzluğu ve boyut şişmesi engellenir.
     */
    @Bean
    public RedisTemplate<String, byte[]> byteRedisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(RedisSerializer.byteArray());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(RedisSerializer.byteArray());
        return template;
    }
}
