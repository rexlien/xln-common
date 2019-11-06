package xln.common.service;


import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import io.lettuce.core.KeyValue;
import io.lettuce.core.ReadFrom;
import lombok.Data;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scripting.ScriptSource;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import xln.common.cache.CacheController;
import xln.common.config.ServiceConfig;
import xln.common.serializer.GenericJackson2JsonRedisSerializer;
import xln.common.serializer.ProtoRedisSerializer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RedisService {

    private static Logger logger = LoggerFactory.getLogger(RedisService.class);

    @Data
    private static class RedisClientSet {
        private AtomicReference<ReactiveRedisTemplate<String, String>> reactStringTemplate = new AtomicReference<>();
        private AtomicReference<ReactiveRedisTemplate<String, Object>> reactObjectTemplate = new AtomicReference<>();

        private AtomicReference<RedisTemplate<String, String>> stringTemplate = new AtomicReference<>();
        private AtomicReference<RedisTemplate<String, Object>> objTemplate = new AtomicReference<>();
        private AtomicReference<CompletableFuture<RedisTemplate<String, Any>>> anyTemplate = new AtomicReference<>();
        private AtomicReference<CompletableFuture<ReactiveRedisTemplate<String, Any>>> reactAnyTemplate = new AtomicReference<>();
        private volatile RedissonClient redisson;
        //private RedisMessageListenerContainer container;
    }

    public static class RedisMessagePublisher  {

        private final RedisTemplate<String, Any> redisTemplate;
        public RedisMessagePublisher(RedisTemplate redisTemplate) {
            this.redisTemplate = redisTemplate;
        }

        public void publish(String topic, Message message) {

            redisTemplate.convertAndSend(topic, Any.pack(message));
        }
    }

    private final ServiceConfig serviceConfig;

    private ConcurrentHashMap<String, LettuceConnectionFactory> connectionFactories = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, RedisClientSet> redisClientSets = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, RedisScript<Object>> redisScripts = new ConcurrentHashMap<String, RedisScript<Object>>();

    private ConcurrentHashMap<String, RedisMessageListenerContainer> messageListenerContainers = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, RedisMessagePublisher> messagePublishers = new ConcurrentHashMap<>();

    public RedisService(ServiceConfig serviceConfig) {
        this.serviceConfig = serviceConfig;
    }

    private static LettuceConnectionFactory clusterConnectionFactory(ServiceConfig.RedisServerConfig config) {

        RedisClusterConfiguration redisConfig = new RedisClusterConfiguration();
        redisConfig.setPassword(config.getPassword());
        for(String s : config.getURI()) {

            URI uri = null;
            try {
                uri = new URI(s);
            }catch(URISyntaxException ex) {
                logger.error(ex.toString());
            }
            if(uri != null) {

                redisConfig.addClusterNode(new RedisNode(uri.getHost(), uri.getPort()));

            }
        }
        if(config.isSlaveRead()) {
            LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                    .readFrom(ReadFrom.SLAVE)
                    .build();

            LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig, clientConfig);
            factory.afterPropertiesSet();
            return factory;
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig);
        factory.afterPropertiesSet();
        return factory;
    }

    private static LettuceConnectionFactory singleConnectionFactory(ServiceConfig.RedisServerConfig config) {

        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setPassword(config.getPassword());
        for(String s : config.getURI()) {
            URI uri = null;
            try {
                uri = new URI(s);
            }catch(URISyntaxException ex) {
                logger.error(ex.toString());
            }
            if(uri != null) {

                redisConfig.setHostName(uri.getHost());
                redisConfig.setPort(uri.getPort());

            }
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig);
        factory.afterPropertiesSet();

        return factory;
    }

    @PostConstruct
    private void init() {
        for(ServiceConfig.RedisScript scriptConfig : serviceConfig.getRedisConfig().getScript()) {

            loadScript(scriptConfig.getName(), scriptConfig.getPath());
        }

        for(Map.Entry<String, ServiceConfig.RedisServerConfig> kv : serviceConfig.getRedisConfig().getRedisServerConfigs().entrySet()) {
            if(kv.getValue().getType() == ServiceConfig.RedisServerConfig.RedisType.SINGLE) {
                connectionFactories.put(kv.getKey(), singleConnectionFactory(kv.getValue()));
                RedisClientSet clientSet = new RedisClientSet();
                if(kv.getValue().isUseRedisson()) {
                    Config config = new Config();
                    SingleServerConfig singleConfig = config.useSingleServer();

                    for (String uri : kv.getValue().getURI()) {
                        singleConfig.setAddress(uri);
                    }
                    singleConfig.setPassword(kv.getValue().getPassword());
                    clientSet.redisson = Redisson.create(config);
                }
                redisClientSets.put(kv.getKey(),clientSet);
            }
            else if(kv.getValue().getType() == ServiceConfig.RedisServerConfig.RedisType.CLUSTER) {

                connectionFactories.put(kv.getKey(), clusterConnectionFactory(kv.getValue()));
                RedisClientSet clientSet = new RedisClientSet();

                if(kv.getValue().isUseRedisson()) {
                    Config config = new Config();
                    ClusterServersConfig clusterConfig = config.useClusterServers();

                    for (String uri : kv.getValue().getURI()) {
                        clusterConfig.addNodeAddress(uri);
                    }
                    clusterConfig.setPassword(kv.getValue().getPassword());
                    clientSet.redisson = Redisson.create(config);
                }
                redisClientSets.put(kv.getKey(), clientSet);



            }
            if(kv.getValue().isPublisher()) {
                messagePublishers.put(kv.getKey(), new RedisMessagePublisher(getTemplate(kv.getKey(), Any.class)));
            }
            if(kv.getValue().isSubscriber()) {
                RedisMessageListenerContainer messageContainer = new RedisMessageListenerContainer();
                messageContainer.setConnectionFactory(getConnectionFactory(kv.getKey()));
                messageContainer.afterPropertiesSet();
                messageContainer.start();

                messageListenerContainers.put(kv.getKey(), messageContainer);
            }
        }

    }

    @PreDestroy
    private void destroy() {
        for(Map.Entry<String, RedisClientSet> kv : redisClientSets.entrySet()) {
            if(kv.getValue().getRedisson() != null) {
                kv.getValue().getRedisson().shutdown();
            }
        }

        for(Map.Entry<String, LettuceConnectionFactory> kv : connectionFactories.entrySet()) {
            kv.getValue().destroy();
        }
    }

    //TODO: thread-safe
    public ReactiveRedisTemplate<String, String> getStringReactiveTemplate(String name) {


        RedisClientSet set = redisClientSets.get(name);
        if (set == null) {
            return null;
        }


        if(set.getReactStringTemplate().get() == null) {
            RedisSerializer<String> serializer = new StringRedisSerializer();
            RedisSerializationContext<String, String> serializationContext = RedisSerializationContext
                    .<String, String>newSerializationContext()
                    .key(serializer)
                    .value(serializer)
                    .hashKey(serializer)
                    .hashValue(serializer)
                    .build();

            ReactiveRedisTemplate<String, String> template = new ReactiveRedisTemplate(connectionFactories.get(name), serializationContext);
            set.getReactStringTemplate().compareAndSet(null, template);//setReactStringTemplate(template);
        }
        return set.getReactStringTemplate().get();

    }

    public ReactiveRedisTemplate<String, Object> getObjectReactiveTemplate(String name) {

        RedisClientSet set = redisClientSets.get(name);
        if (set == null) {
            return null;
        }

        if(set.getReactObjectTemplate().get() == null) {

            RedisSerializationContext<String, Object> serializationContext = RedisSerializationContext
                    .<String, Object>newSerializationContext(new StringRedisSerializer()).key(new StringRedisSerializer())
                    .value(new GenericJackson2JsonRedisSerializer()).hashValue(new GenericJackson2JsonRedisSerializer()).build();


            ReactiveRedisTemplate<String, Object> template = new ReactiveRedisTemplate(connectionFactories.get(name), serializationContext);
            set.getReactObjectTemplate().compareAndSet(null, template);
        }
        return set.getReactObjectTemplate().get();
    }

    public ReactiveRedisTemplate<String, Any> getAnyReactiveTemplate(String name) {

        RedisClientSet set = redisClientSets.get(name);
        if (set == null) {
            return null;
        }

        if(set.getReactAnyTemplate().get() == null) {

            CompletableFuture<ReactiveRedisTemplate<String, Any>> future = new CompletableFuture<>();
            if (set.getReactAnyTemplate().compareAndSet(null, future)) {

                RedisSerializationContext<String, Any> serializationContext = RedisSerializationContext
                        .<String, Any>newSerializationContext(new StringRedisSerializer()).key(new StringRedisSerializer())
                        .value(new ProtoRedisSerializer())
                        .hashValue(new ProtoRedisSerializer()).build();

                ReactiveRedisTemplate<String, Any> template = new ReactiveRedisTemplate<>(connectionFactories.get(name), serializationContext);


                //template.setValueSerializer(new ProtoRedisSerializer());
                //template.afterPropertiesSet();
                future.complete(template);
                return template;

            }
        }

        try {
            return set.getReactAnyTemplate().get().get();
        } catch (Exception ex) {

            logger.error("", ex);
            return null;

        }

    }

    private RedisTemplate<String, Object> getObjectTemplate(String name) {
        RedisClientSet set = redisClientSets.get(name);
        if (set == null) {
            return null;
        }
        if(set.getObjTemplate().get() == null) {

            RedisTemplate<String, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(connectionFactories.get(name));
            template.setDefaultSerializer(new StringRedisSerializer());
            template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
            template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
            template.afterPropertiesSet();
            set.getObjTemplate().compareAndSet(null, template);
        }
        return set.getObjTemplate().get();

    }

    private RedisTemplate<String, Any> getAnyTemplate(String name) {
        RedisClientSet set = redisClientSets.get(name);
        if (set == null) {
            return null;
        }

        if(set.getObjTemplate().get() == null) {

            CompletableFuture<RedisTemplate<String, Any>> future = new CompletableFuture<>();
            if (set.getAnyTemplate().compareAndSet(null, future)) {

                RedisTemplate<String, Any> template = new RedisTemplate<>();
                template.setConnectionFactory(connectionFactories.get(name));
                template.setDefaultSerializer(new StringRedisSerializer());
                template.setHashValueSerializer(new ProtoRedisSerializer());
                template.setValueSerializer(new ProtoRedisSerializer());
                template.afterPropertiesSet();
                future.complete(template);
                return template;

            }
        }

        try {
            return set.getAnyTemplate().get().get();
        } catch (Exception ex) {

            logger.error("", ex);
            return null;

        }


    }


    private RedisTemplate<String, String> getStringTemplate(String name) {

        RedisClientSet set = redisClientSets.get(name);
        if (set == null) {
            return null;
        }

        if(set.getStringTemplate().get() == null) {

            RedisTemplate<String, String> template = new RedisTemplate<String, String>();
            template.setConnectionFactory(connectionFactories.get(name));
            template.setDefaultSerializer(new StringRedisSerializer());
            template.afterPropertiesSet();
            set.getStringTemplate().compareAndSet(null, template);
        }
        return set.getStringTemplate().get();

    }

    public <T extends Object> RedisTemplate<String, T> getTemplate(String name, Class<T> valueType) {

        if(valueType == String.class) {
            return (RedisTemplate<String, T>) getStringTemplate(name);
        } else if(valueType == Any.class) {
            return (RedisTemplate<String, T>) getAnyTemplate(name);
        }
        else {
            return (RedisTemplate<String, T>) getObjectTemplate(name);
        }

    }

    public <T extends Object> ReactiveRedisTemplate<String, T> getReactiveTemplate(String name, Class<T> valueType) {

        if(valueType == String.class) {
            return (ReactiveRedisTemplate<String, T>) getStringReactiveTemplate(name);
        } else if(valueType == Any.class){
            return (ReactiveRedisTemplate<String, T>)getAnyReactiveTemplate(name);
        } else {
            return (ReactiveRedisTemplate<String, T>) getObjectReactiveTemplate(name);
        }

    }

    public LettuceConnectionFactory getConnectionFactory(String name) {
        return connectionFactories.get(name);
    }

    public RedisScript<Object> loadScript(String name, String path) {
        ScriptSource scriptSource = new ResourceScriptSource(new ClassPathResource(path));
        try {
            RedisScript<Object> script = RedisScript.of(scriptSource.getScriptAsString(), Object.class);
            redisScripts.put(name, script);
            return script;
        }catch(IOException ex) {
            logger.error("load {} failed", name);
            return null;
        }
    }

    public boolean containsServer(String name) {
        return (redisClientSets.get(name) != null);
    }


    public RedisScript<Object> getScript(String name) {
        return redisScripts.get(name);
    }

/*
    public Flux<Object> runScript(String serverName, String name, List<String> keyParams, List<String> argParams) {

        ReactiveRedisTemplate<String, Object> template = getObjectReactiveTemplate(serverName);
        if(template == null)
            return null;

        RedisScript<Object> script = redisScripts.get(name);
        if(script != null)
            return template.execute(script, keyParams, argParams);
        else {
            return null;
        }
    }
*/
    public Flux<Object> runScript(ReactiveRedisTemplate<String, Object> redisTemplate, String scriptName, List<String> keyParams, List<String> argParams) {

        RedisScript<Object> script = redisScripts.get(scriptName);
        if(script != null)
            return redisTemplate.execute(script, keyParams, argParams);
        else {
            return null;
        }
    }

    public Object runScript(RedisTemplate<String, Object> redisTemplate, String scriptName, List<String> keyParams, Object... argParams) {

        RedisScript<Object> script = redisScripts.get(scriptName);
        if(script != null)
            return redisTemplate.execute(script, keyParams, argParams);
        else {
            return null;
        }
    }

    public RedissonClient getRedisson(String name) {
        RedissonClient redissonClient = redisClientSets.get(name).getRedisson();
        if(redissonClient == null) {
            logger.error("redisson client not enable");
        }
        return redissonClient;
    }

    public RedisMessagePublisher getMessagePublisher(String name) {
        return messagePublishers.getOrDefault(name, null);
    }

    public RedisMessageListenerContainer getMessageListener(String name) {
        return messageListenerContainers.getOrDefault(name, null);
    }



}
