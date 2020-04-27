package xln.common.test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.swagger.models.auth.In;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import xln.common.cache.CacheController;
import xln.common.service.RateLimiter;
import xln.common.service.RedisService;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@Slf4j
public class RedisTest {


    private static Logger logger = LoggerFactory.getLogger(RedisTest.class);

    @Autowired
    private RedisService redisService;

    @Autowired
    private CacheController cacheController;


    @Autowired
    private RateLimiter rateLimiter;

    @Test
    public void runScript() {


        redisService.runScript(redisService.getReactiveTemplate("redis0", Object.class), "saddAndGetSize", Collections.singletonList("testKey123"), Collections.singletonList("testValue")).
                publishOn(Schedulers.elastic()).subscribe(obj -> {
                    Gson gson = new Gson();
                    Type type = new TypeToken<List<Integer>>(){}.getType();
                    List<Integer> list = gson.fromJson(obj.toString(), type);

                    for(int i : list) {
                        logger.warn("res: {}", i);
                    }
                });

    }

    @Test
    public void testPubSub() {

        Semaphore resultLock = new Semaphore(0);

        cacheController.subscribeMessageSrc( (object)-> {
                    log.info(object.toString());
                    resultLock.release();
                });

        try {
            while(!resultLock.tryAcquire(500, TimeUnit.MILLISECONDS )) {
                cacheController.publishCacheInvalidation(new CacheController.CacheInvalidateTask("caffeine", "cache", "cache"));
            }

        }catch (Exception ex) {

        }

    }
    @Test
    public void rateLimit() throws Exception {

        var acquireInfo = rateLimiter.acquireCount("test::rate-limit", 100);

        Thread.sleep(3000);
        rateLimiter.releaseCount(acquireInfo.block().key).block();

        acquireInfo = rateLimiter.acquireCount("test::rate-limit", 5000);
        rateLimiter.releaseCount(acquireInfo.block().key).block();

    }

}
