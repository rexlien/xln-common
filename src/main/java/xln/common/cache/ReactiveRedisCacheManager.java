package xln.common.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.support.AbstractCacheManager;
import org.springframework.cache.transaction.AbstractTransactionSupportingCacheManager;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.lang.Nullable;
import xln.common.config.CacheConfig;
import xln.common.service.RedisService;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class ReactiveRedisCacheManager extends AbstractTransactionSupportingCacheManager {

    private final RedisCacheConfiguration initConfig;
    private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;

    public ReactiveRedisCacheManager(ReactiveRedisTemplate<String, Object> reactiveRedisTemplate, RedisCacheConfiguration redisCacheConfiguration) {
        this.initConfig = redisCacheConfiguration;
        this.reactiveRedisTemplate = reactiveRedisTemplate;
    }
    @Override
    protected Collection<? extends Cache> loadCaches() {
        List<ReactiveRedisCache> list = new LinkedList();

        return list;
    }

    @Override
    protected Cache getMissingCache(String name) {
        return createRedisCache(name, initConfig);
    }

    protected ReactiveRedisCache createRedisCache(String name, RedisCacheConfiguration cacheConfig) {

        return new ReactiveRedisCache(name, reactiveRedisTemplate, cacheConfig, initConfig.getAllowCacheNullValues());
    }




}
