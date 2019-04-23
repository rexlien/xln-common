package xln.common.test;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import xln.common.annotation.XLNCacheable;


public class TestProxy {

    @XLNCacheable(cacheManagerID = "redis0")
    @Cacheable(cacheResolver = "xln-CacheResolver", cacheNames = "myKey")
    public String cache(String key) {
        return "test";
    }



}
