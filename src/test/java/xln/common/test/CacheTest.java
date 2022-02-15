package xln.common.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import xln.common.service.CacheService;
import xln.common.service.RedisService;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@EnableCaching
@Slf4j
public class CacheTest {

    @Autowired
    private TestProxy proxy;

    @Autowired
    private RedisService redisService;


    @Autowired
    private CacheService cacheService;

    @TestConfiguration
    public static class TestConfig {

        @Bean
        public TestProxy cacheClass() {
            return new TestProxy();
        }
    }

    @Test
    public void testRedisCache() {

        String cached = proxy.cache("key");
        Cache cache =  cacheService.getCacheManager("redis0").getCache("myCache");
        Assert.assertTrue(cache.get("key", String.class).equals("value"));

        RedisTemplate<String, Object> template =  redisService.getTemplate("redis0", Object.class);
        Object str = template.opsForValue().get("myKey::key");
        Assert.assertTrue(str.equals(cached));

    }

    @Test
    public void testCaffeineCache() {
        String cached = proxy.caffieneCache("key");
        Cache cache =  cacheService.getCacheManager(CacheService.CAFFEINE_CACHE_MANAGER_NAME).getCache("ca0");

        Assert.assertTrue(cache.get("key", String.class).equals(cached));


        CaffeineCache caffeineCache = (CaffeineCache)cache;
        proxy.caffieneCache("key2");
        caffeineCache.getNativeCache().cleanUp();

        Assert.assertTrue(caffeineCache.getNativeCache().estimatedSize() == 1);

    }

    @Test
    public void testNativeCache() {

        String cached = proxy.caffieneCache("key");

        var map = cacheService.<String, Object>asLocalMap(CacheService.CAFFEINE_CACHE_MANAGER_NAME, "ca0");
        for (var e : map.entrySet()) {
            Assert.assertTrue(e.getKey() == "key");
            Assert.assertTrue(e.getValue() == cached);
        }
        


    }


}
