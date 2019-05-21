package xln.common.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
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

@Service
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
            String cacheName = entry.getKey();

            RedisSerializationContext.SerializationPair<Object> jsonSerializer =
                    RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer());

            //RedisTemplate template = redisService.getTemplate(config.getRedisServerName(), Object.class);
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

    }

    public CacheManager getCacheManager(String name) {
        return cacheManagers.get(name);

    }

}
