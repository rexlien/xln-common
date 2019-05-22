package xln.common.service;


import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.config.CacheManagementConfigUtils;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.stereotype.Service;
import xln.common.cache.CustomCacheResolver;
import xln.common.config.CacheConfig;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CacheService {


    @Bean(name = "xln-CacheResolver")
    public CacheResolver cacheResolver() {
        return new CustomCacheResolver(this);
    }

    @Autowired
    private RedisService redisService;

    @Autowired
    private CacheConfig cacheConfig;

    private ConcurrentMap<String, CacheManager> cacheManagers = new ConcurrentHashMap<>();

    @PostConstruct
    private void init() {

        for(Map.Entry<String, CacheConfig.RedisCacheConfig> entry : cacheConfig.getRedisCacheConfig().entrySet()) {

            CacheConfig.RedisCacheConfig config = entry.getValue();

            if(!redisService.containsServer(config.getRedisServerName())) {
                log.error("cache server not exist:" + config.getRedisServerName());
                continue;

            }
            String cacheName = entry.getKey();

            RedisSerializationContext.SerializationPair<Object> jsonSerializer =
                    RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer());

            RedisCacheConfiguration redisCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig();
            if(!config.isAddPrefix())
                redisCacheConfiguration = redisCacheConfiguration.disableKeyPrefix();
            if(!config.isCacheWhenNull())
                redisCacheConfiguration = redisCacheConfiguration.disableCachingNullValues();
            redisCacheConfiguration = redisCacheConfiguration.entryTtl(Duration.ofMillis(config.getTtl())).serializeValuesWith(jsonSerializer);

            RedisCacheManager redisCacheManager = RedisCacheManager.RedisCacheManagerBuilder.fromConnectionFactory(
                    redisService.getConnectionFactory(config.getRedisServerName())).cacheDefaults(redisCacheConfiguration).build();

            cacheManagers.put(cacheName, redisCacheManager);
        }


        for(Map.Entry<String, CacheConfig.CaffeineConfig> entry : cacheConfig.getCaffeineConfig().entrySet()) {

            CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
            Caffeine<Object, Object> builder = Caffeine.newBuilder();

            if(entry.getValue().getMaxSize() > 0) {
                builder = builder.maximumSize(entry.getValue().getMaxSize());
            }
            if(entry.getValue().getExpireAccess() > 0) {
                builder = builder.expireAfterAccess(entry.getValue().getExpireAccess(), TimeUnit.MILLISECONDS);
            }
            caffeineCacheManager.setCaffeine(builder);
            cacheManagers.put(entry.getKey(), caffeineCacheManager);
        }


    }

    public CacheManager getCacheManager(String name) {
        return cacheManagers.get(name);

    }

}
