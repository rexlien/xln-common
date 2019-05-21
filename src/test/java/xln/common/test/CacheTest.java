package xln.common.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
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

    @TestConfiguration
    public static class TestConfig {

        @Bean
        public TestProxy cacheClass() {
            return new TestProxy();
        }
    }

    @Test
    public void testCache() {

        String cached = proxy.cache("key");

        RedisTemplate<String, Object> template =  redisService.getTemplate("redis0", Object.class);
        Object str = template.opsForValue().get("myKey::key");
        Assert.assertTrue(str.equals("test"));


    }

}
