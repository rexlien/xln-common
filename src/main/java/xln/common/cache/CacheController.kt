package xln.common.cache

import com.google.protobuf.InvalidProtocolBufferException
import lombok.extern.slf4j.Slf4j
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.stereotype.Service
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import xln.common.config.CacheConfig
import xln.common.etcd.ConfigStore
import xln.common.proto.command.Command.CacheTask
import xln.common.service.CacheService
import xln.common.service.RedisService
import xln.common.service.RedisService.RedisMessagePublisher
import java.util.function.Consumer
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

@Service
class CacheController(private val redisService: RedisService, private val cacheService: CacheService, private val cacheConfig: CacheConfig) {


    private val log = LoggerFactory.getLogger(this.javaClass)

    @Volatile
    private var messageListenerContainer: RedisMessageListenerContainer? = null

    @Volatile
    private var messagePublisher: RedisMessagePublisher? = null

    @Volatile
    private var subRedisTemplate: RedisTemplate<String, com.google.protobuf.Any>? = null

    @Volatile
    private var topic: String? = null

    @Volatile
    private var cacheSubscriber: Disposable? = null

    @Volatile
    private var messageSrc: Flux<Any>? = null

    @PostConstruct
    private fun init() {
    }

    @PreDestroy
    private fun destroy() {

        cacheSubscriber?.dispose()
    }

    fun publishCacheInvalidation(task: CacheTask?) {
        messagePublisher?.publish(topic, task)
    }

    fun subscribeMessageSrc(consumer: Consumer<Any>?): Disposable? {
        return if (messageListenerContainer != null && messageSrc != null) {
            messageSrc?.subscribe(consumer)
        } else null
    }

    //private Flux<Object> sub
    init {
        if (cacheConfig.cacheControllerConfig != null && cacheConfig.cacheControllerConfig.redisServerName != null) {
            val redisServer = cacheConfig.cacheControllerConfig.redisServerName
            topic = cacheConfig.cacheControllerConfig.topicPattern
            subRedisTemplate = redisService.getTemplate(redisServer, com.google.protobuf.Any::class.java)
            if (cacheConfig.cacheControllerConfig.isPublisher) {
                messagePublisher = redisService.getMessagePublisher(cacheConfig.cacheControllerConfig.redisServerName) //new RedisMessagePublisher(this.pubRedisTemplate);
            }
            if (cacheConfig.cacheControllerConfig.isSubscriber) {
                messageListenerContainer = redisService.getMessageListener(cacheConfig.cacheControllerConfig.redisServerName)
                messageSrc = Flux.create { emitter: FluxSink<Any> ->
                    messageListenerContainer?.addMessageListener(MessageListener { message, pattern -> //Object object = subRedisTemplate.getValueSerializer().deserialize(message.getBody());
                        val any = subRedisTemplate?.valueSerializer?.deserialize(message.body) as com.google.protobuf.Any
                        //if( instanceof CacheInvalidateTask) {
                        if (any.`is`(CacheTask::class.java)) {
                            //CacheInvalidateTask cacheTask = (CacheInvalidateTask)object;
                            val cacheTask: CacheTask = try {
                                any.unpack(CacheTask::class.java)
                            } catch (ex: InvalidProtocolBufferException) {
                                return@MessageListener
                            }
                            /*
                            val cacheManager = cacheService.getCacheManager(cacheTask.cacheManagerName.value)
                            if (cacheManager != null) {
                                if (!cacheTask.hasCacheName()) {
                                    log.debug("Cache Manager Clearing: " + cacheTask.cacheManagerName.value)
                                    for (cacheName in cacheManager.cacheNames) {
                                        val cache = cacheManager.getCache(cacheName)
                                        cache?.clear()
                                    }
                                } else {
                                    val cache = cacheManager.getCache(cacheTask.cacheName.value)
                                    if (cache != null) {
                                        if (!cacheTask.hasKey()) {
                                            log.debug("Cache Clearing: " + cacheTask.cacheName.value)
                                            cache.clear()
                                        } else {
                                            log.debug("Cache Key Evicting: " + cacheTask.cacheName.value + cacheTask.key.value)
                                            cache.evict(cacheTask.key.value)
                                        }
                                    }
                                }
                            }

                             */
                            cacheService.invalidCache(cacheTask)
                        }
                        if (any != null) {
                            emitter.next(any)
                        }
                    }, listOf(PatternTopic(topic)))
                }
                cacheSubscriber = messageSrc?.subscribe()
            }
        }
    }
}