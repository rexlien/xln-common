package xln.common.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.validation.annotation.Validated;
import xln.common.service.RedisService;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix="xln.cache-config")
@Configuration
@Data
@EnableCaching
public class CacheConfig {

    @Data
    public static class RedisCacheConfig {
        private String redisServerName;
        private int ttl = 0;
        private boolean addPrefix = true;
        private boolean cacheWhenNull = true;

    }

    @Data
    public static class CaffeineConfig {
        private int maxSize = -1;

        public enum ExpirePolicy {
            ACCESS("access"),
            WRITE("write"),
            CREATION("creation");

            private String type;

            ExpirePolicy(String type) {
                this.type = type;
            }
        }

        ExpirePolicy expirePolicy = ExpirePolicy.WRITE;
        private int expireTime = -1;

    }

    private Map<String, RedisCacheConfig> redisCacheConfig = new HashMap<>();
    private Map<String, CaffeineConfig> caffeineConfig = new HashMap<>();



}
