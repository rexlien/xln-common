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
@EnableCaching
public class CacheConfig {


    public Map<String, RedisCacheConfig> getRedisCacheConfig() {
        return redisCacheConfig;
    }

    public CacheConfig setRedisCacheConfig(Map<String, RedisCacheConfig> redisCacheConfig) {
        this.redisCacheConfig = redisCacheConfig;
        return this;
    }

    public Map<String, CaffeineConfig> getCaffeineConfig() {
        return caffeineConfig;
    }

    public CacheConfig setCaffeineConfig(Map<String, CaffeineConfig> caffeineConfig) {
        this.caffeineConfig = caffeineConfig;
        return this;
    }

    public CacheControllerConfig getCacheControllerConfig() {
        return cacheControllerConfig;
    }

    public CacheConfig setCacheControllerConfig(CacheControllerConfig cacheControllerConfig) {
        this.cacheControllerConfig = cacheControllerConfig;
        return this;
    }

    public static class RedisCacheConfig {
        private String redisServerName;
        private int ttl = 0;
        private boolean addPrefix = true;
        private boolean cacheWhenNull = false;

        public String getRedisServerName() {
            return redisServerName;
        }

        public RedisCacheConfig setRedisServerName(String redisServerName) {
            this.redisServerName = redisServerName;
            return this;
        }

        public int getTtl() {
            return ttl;
        }

        public RedisCacheConfig setTtl(int ttl) {
            this.ttl = ttl;
            return this;
        }

        public boolean isAddPrefix() {
            return addPrefix;
        }

        public RedisCacheConfig setAddPrefix(boolean addPrefix) {
            this.addPrefix = addPrefix;
            return this;
        }

        public boolean isCacheWhenNull() {
            return cacheWhenNull;
        }

        public RedisCacheConfig setCacheWhenNull(boolean cacheWhenNull) {
            this.cacheWhenNull = cacheWhenNull;
            return this;
        }
    }


    public static class CaffeineConfig {
        private int maxSize = -1;

        public int getMaxSize() {
            return maxSize;
        }

        public CaffeineConfig setMaxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public ExpirePolicy getExpirePolicy() {
            return expirePolicy;
        }

        public CaffeineConfig setExpirePolicy(ExpirePolicy expirePolicy) {
            this.expirePolicy = expirePolicy;
            return this;
        }

        public int getExpireTime() {
            return expireTime;
        }

        public CaffeineConfig setExpireTime(int expireTime) {
            this.expireTime = expireTime;
            return this;
        }

        public enum ExpirePolicy {
            ACCESS("access"),
            WRITE("write"),
            CREATION("creation");

            private String type;

            ExpirePolicy(String type) {
                this.type = type;
            }
        }

        private ExpirePolicy expirePolicy = ExpirePolicy.WRITE;
        private int expireTime = -1;

    }


    public static class CacheControllerConfig {
        private String redisServerName = "";
        private String topicPattern = "cache-tasks";
        private boolean publisher = false;
        private boolean subscriber = false;

        public String getRedisServerName() {
            return redisServerName;
        }

        public CacheControllerConfig setRedisServerName(String redisServerName) {
            this.redisServerName = redisServerName;
            return this;
        }

        public String getTopicPattern() {
            return topicPattern;
        }

        public CacheControllerConfig setTopicPattern(String topicPattern) {
            this.topicPattern = topicPattern;
            return this;
        }

        public boolean isPublisher() {
            return publisher;
        }

        public CacheControllerConfig setPublisher(boolean publisher) {
            this.publisher = publisher;
            return this;
        }

        public boolean isSubscriber() {
            return subscriber;
        }

        public CacheControllerConfig setSubscriber(boolean subscriber) {
            this.subscriber = subscriber;
            return this;
        }
    }

    private Map<String, RedisCacheConfig> redisCacheConfig = new HashMap<>();
    private Map<String, CaffeineConfig> caffeineConfig = new HashMap<>();
    private CacheControllerConfig cacheControllerConfig = null;



}
