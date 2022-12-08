package xln.common.test

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.junit.Test
import org.junit.runner.RunWith
import org.redisson.RedissonReactive
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import reactor.core.scheduler.Schedulers
import xln.common.cache.CacheController
import xln.common.service.RateLimiter
import xln.common.service.RedisService
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger {}

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [TestApplication::class])
@ActiveProfiles("test")
class RedisTest {
    @Autowired
    private val redisService: RedisService? = null

    @Autowired
    private val cacheController: CacheController? = null

    @Autowired
    private val rateLimiter: RateLimiter? = null

    //private var redisson: RedissonReactive? = null


    @Test
    fun runScript() {
        redisService!!.runScript(
            redisService.getReactiveTemplate("redis0", Any::class.java),
            "saddAndGetSize",
            listOf("testKey123"),
            listOf<Any>("testValue")
        ).publishOn(
            Schedulers.elastic()
        ).subscribe { obj: Any ->
            val gson = Gson()
            val type = object : TypeToken<List<Int?>?>() {}.type
            val list = gson.fromJson<List<Int>>(obj.toString(), type)
            for (i in list) {
                log.warn("res: {}", i)
            }
        }
    }

    @Test
    fun testPubSub() {
        val resultLock = Semaphore(0)
        cacheController!!.subscribeMessageSrc { `object`: Any ->
            log.info(`object`.toString())
            resultLock.release()
        }
        try {
            /*
            while(!resultLock.tryAcquire(500, TimeUnit.MILLISECONDS )) {
                cacheController.publishCacheInvalidation(new CacheController.CacheInvalidateTask("caffeine", "cache", "cache"));
            }

             */
        } catch (ex: Exception) {
        }
    }

    @Test
    @Throws(Exception::class)
    fun rateLimit() {
        var acquireInfo = rateLimiter!!.acquireCount("test::rate-limit", 500, false)
        Thread.sleep(3000)
        rateLimiter.releaseCount(acquireInfo.block().key).block()
        acquireInfo = rateLimiter.acquireCount("test::rate-limit", 500, false)
        rateLimiter.releaseCount(acquireInfo.block().key).block()
    }

    @Test
    @Throws(Exception::class)
    fun radissonLock() {

        runBlocking {
            val redisson = redisService!!.getRedisson("redis0") as RedissonReactive

            val lock = redisson!!.getPermitExpirableSemaphore("testSemaphore");
            lock.trySetPermits(1).awaitSingle()
            val threadId = Thread.currentThread().id
            val locked = lock.tryAcquire(5, 40, TimeUnit.SECONDS).awaitFirstOrNull()
            if(locked != null) {
                log.info("outer locked")
            }
            withContext(Dispatchers.IO) {
                val lock = redisson!!.getPermitExpirableSemaphore("testSemaphore");
                lock.trySetPermits(1).awaitSingle()
                val threadId2 = Thread.currentThread().id
                val locked = lock.tryAcquire(30, 3, TimeUnit.SECONDS).awaitFirstOrNull()
                if(locked != null) {
                    log.info("inner locked")
                    withContext(Dispatchers.IO) {
                        lock.release(locked).awaitFirstOrNull()
                        log.info("inner unlocked")
                    }
                }
            }
            if(locked != null) {
                withContext(Dispatchers.IO) {
                    try {
                        lock.release(locked).awaitFirstOrNull()
                    }catch (e : Exception) {
                        log.info("", e)
                    }
                    log.info("outer unlocked")
                }

            }
            log.info("Done")
        }
        //redisson.
        //redisService.get
    }


}