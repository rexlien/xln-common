package xln.common.cache;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.omg.CORBA.OBJECT_NOT_EXIST;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.SynchronousSink;
import xln.common.service.CacheService;
import xln.common.service.RedisService;

import javax.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.util.Collections;

@Service
public class CacheController {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CacheInvalidateTask {

        private String cacheName;
        private String key;

    }

    public static class RedisMessagePublisher  {

        private RedisTemplate<String, Object> redisTemplate;
        public RedisMessagePublisher(RedisTemplate redisTemplate) {
            this.redisTemplate = redisTemplate;
        }

        public void publish(String topic, Object task) {

            redisTemplate.convertAndSend(topic, task);
        }
    }
    private RedisMessageListenerContainer messageContainer;
    private RedisMessagePublisher messagePublisher;

    private RedisTemplate<String, Object> redisTemplate;
    private RedisService redisService;
    private CacheService cacheService;
    public CacheController(RedisService redisService, CacheService cacheService) {

        this.redisService = redisService;
        this.cacheService = cacheService;
    }

    @PostConstruct
    private void init() {


    }

    public void asPublisher(String serverName) {

        this.redisTemplate = redisService.getTemplate(serverName, Object.class);
        messagePublisher = new RedisMessagePublisher(this.redisTemplate);

    }
    public void asSubscriber(String serverName) {

        redisTemplate = redisService.getTemplate(serverName, Object.class);
        messageContainer = new RedisMessageListenerContainer();
        messageContainer.setConnectionFactory(redisService.getConnectionFactory(serverName));
        messageContainer.afterPropertiesSet();
        messageContainer.start();

    }

    public Flux<Object> addSubscription() {

        return Flux.create( emitter -> {
            messageContainer.addMessageListener(new MessageListener() {
                @Override
                public void onMessage(Message message, byte[] pattern) {
                    Object object = redisTemplate.getValueSerializer().deserialize(message.getBody());
                    if(object instanceof CacheInvalidateTask) {
                        CacheInvalidateTask cacheTask = (CacheInvalidateTask)object;
                        CacheManager cacheManager = cacheService.getCacheManager(cacheTask.getCacheName());
                        if(cacheManager != null) {
                            Cache cache = cacheManager.getCache(cacheTask.getCacheName());
                            if(cache != null) {
                                cache.evict(cacheTask.getKey());
                            }
                        }
                    }
                    emitter.next(object);
                    return;
                }
            }, Collections.singletonList(new PatternTopic("cache-tasks")));
            //
        });


    }

    public void publishCacheInvalidation(CacheInvalidateTask task) {

        messagePublisher.publish("cache-tasks", task);

    }


}
