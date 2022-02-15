package xln.common.test;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import xln.common.annotation.XLNCacheable;
import xln.common.service.CacheService;


public class TestProxy {

    @XLNCacheable(cacheManagerID = "redis0")
    @Cacheable(cacheResolver = "xln-CacheResolver", cacheNames = "redis0")
    public String cache(String key) {
        return "value";
    }

    @XLNCacheable(cacheManagerID = CacheService.CAFFEINE_CACHE_MANAGER_NAME)
    @Cacheable(cacheResolver = "xln-CacheResolver", cacheNames = "ca0")
    public String caffieneCache(String key) {
        return "value";
    }


}
