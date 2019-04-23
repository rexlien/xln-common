package xln.common.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.config.CacheManagementConfigUtils;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.RedisTemplate;
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
            String redisName = entry.getKey();

            RedisTemplate template = redisService.getStringTemplate(redisName);
            RedisCacheConfiguration redisCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig();
            if(config.isAddPrefix())
                redisCacheConfiguration.usePrefix();
            if(!config.isCacheWhenNull())
                redisCacheConfiguration.disableCachingNullValues();
            redisCacheConfiguration.entryTtl(Duration.ofMillis(config.getTtl()));

            RedisCacheManager redisCacheManager = RedisCacheManager.RedisCacheManagerBuilder.fromConnectionFactory(
                    template.getConnectionFactory()).build();

            cacheManagers.put(redisName, redisCacheManager);
        }

    }

    public CacheManager getCacheManager(String name) {
        return cacheManagers.get(name);

    }

}
