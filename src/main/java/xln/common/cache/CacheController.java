package xln.common.cache;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.SynchronousSink;
import xln.common.config.CacheConfig;
import xln.common.proto.command.Command;
import xln.common.service.CacheService;
import xln.common.service.RedisService;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Service
@Slf4j
public class CacheController {


    @Deprecated
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CacheInvalidateTask {


        private String cacheManagerName;
        private String cacheName;
        private String key;
        //private int command = -1;

    }



    private final CacheConfig cacheConfig;
    private volatile RedisMessageListenerContainer messageListenerContainer;// = new RedisMessageListenerContainer();

    private volatile RedisService.RedisMessagePublisher messagePublisher;

    private volatile RedisTemplate<String, Any> subRedisTemplate;
    private final RedisService redisService;
    private final CacheService cacheService;
    private volatile String topic;
    private volatile Disposable cacheSubscriber = null;
    private volatile Flux<Object> messageSrc = null;

    //private Flux<Object> sub

    public CacheController(RedisService redisService, CacheService cacheService, CacheConfig cacheConfig) {

        this.redisService = redisService;
        this.cacheService = cacheService;
        this.cacheConfig = cacheConfig;

        if(this.cacheConfig.getCacheControllerConfig() != null && cacheConfig.getCacheControllerConfig().getRedisServerName() != null) {
            String redisServer = this.cacheConfig.getCacheControllerConfig().getRedisServerName();
            this.topic = this.cacheConfig.getCacheControllerConfig().getTopicPattern();
            this.subRedisTemplate = redisService.getTemplate(redisServer, Any.class);

            if(this.cacheConfig.getCacheControllerConfig().isPublisher()) {
                messagePublisher = this.redisService.getMessagePublisher(cacheConfig.getCacheControllerConfig().getRedisServerName());//new RedisMessagePublisher(this.pubRedisTemplate);
            }
            if(this.cacheConfig.getCacheControllerConfig().isSubscriber()) {
                messageListenerContainer = this.redisService.getMessageListener(cacheConfig.getCacheControllerConfig().getRedisServerName());

                messageSrc = Flux.create( emitter -> {
                    messageListenerContainer.addMessageListener(new MessageListener() {
                        @Override
                        public void onMessage(Message message, byte[] pattern) {

                            //Object object = subRedisTemplate.getValueSerializer().deserialize(message.getBody());
                            Any any = (Any)subRedisTemplate.getValueSerializer().deserialize(message.getBody());
                            //if( instanceof CacheInvalidateTask) {
                            if(any.is(Command.CacheTask.class)) {
                                //CacheInvalidateTask cacheTask = (CacheInvalidateTask)object;
                                Command.CacheTask cacheTask = null;
                                try {
                                    cacheTask = any.unpack(Command.CacheTask.class);
                                }catch (InvalidProtocolBufferException ex) {

                                    return;
                                }

                                CacheManager cacheManager = cacheService.getCacheManager(cacheTask.getCacheManagerName().getValue());
                                if(cacheManager != null) {
                                    if(!cacheTask.hasCacheName()) {
                                        log.debug("Cache Manager Clearing: " + cacheTask.getCacheManagerName().getValue());
                                        for(String cacheName : cacheManager.getCacheNames()) {
                                            Cache cache = cacheManager.getCache(cacheName);
                                            if(cache != null) {
                                                cache.clear();
                                            }
                                        }

                                    } else {
                                        Cache cache = cacheManager.getCache(cacheTask.getCacheName().getValue());
                                        if (cache != null) {

                                            if (!cacheTask.hasKey()) {
                                                log.debug("Cache Clearing: " + cacheTask.getCacheName().getValue());
                                                cache.clear();
                                            } else {
                                                log.debug("Cache Key Evicting: " + cacheTask.getCacheName().getValue() + cacheTask.getKey().getValue());
                                                cache.evict(cacheTask.getKey().getValue());
                                            }
                                        }
                                    }
                                }
                            }
                            if(any != null) {
                                emitter.next(any);
                            }

                        }
                    }, Collections.singletonList(new PatternTopic(topic)));
                    //
                });
                cacheSubscriber = messageSrc.subscribe();

            }
        }
    }

    @PostConstruct
    private void init() {


    }

    @PreDestroy
    private void destroy() {
        if(cacheSubscriber != null) {
            cacheSubscriber.dispose();
        }
    }

    @Deprecated
    public void publishCacheInvalidation(CacheInvalidateTask task) {

        if(messagePublisher != null) {
            //messagePublisher.publish(topic, task);
        }
    }

    public void publishCacheInvalidation(Command.CacheTask task) {

        if(messagePublisher != null) {
            messagePublisher.publish(topic, task);
        }
    }

    public Disposable subscribeMessageSrc(Consumer<Object> consumer) {
        if(messageListenerContainer != null && messageSrc != null) {
            return messageSrc.subscribe(consumer);
        }
        return null;
    }


}
