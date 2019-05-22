package xln.common.test;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import xln.common.annotation.XLNCacheable;


public class TestProxy {

    @XLNCacheable(cacheManagerID = "redis0")
    @Cacheable(cacheResolver = "xln-CacheResolver", cacheNames = "myCache")
    public String cache(String key) {
        return "value";
    }

    @XLNCacheable(cacheManagerID = "ca0")
    @Cacheable(cacheResolver = "xln-CacheResolver", cacheNames = "myCache")
    public String caffieneCache(String key) {
        return "value";
    }


}
