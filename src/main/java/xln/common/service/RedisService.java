package xln.common.service;


import io.lettuce.core.ReadFrom;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scripting.ScriptSource;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import xln.common.config.RedisConfig;
import xln.common.config.ServiceConfig;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RedisService {

    private static Logger logger = LoggerFactory.getLogger(RedisService.class);

    @Data
    private static class RedisTemplateSet {
        private ReactiveRedisTemplate<String, String> reactStringTemplate;
        private ReactiveRedisTemplate<String, Object> reactObjectTemplate;
        private RedisTemplate<String, String> stringTemplate;
        private RedisTemplate<String, Object> objTemplate;
    }
    @Autowired
    private ServiceConfig serviceConfig;

    @Autowired
    @Qualifier("xln-redisReactiveObjTemplate")
    private ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;


    private HashMap<String, LettuceConnectionFactory> connectionFactories = new HashMap<>();
    private HashMap<String, RedisTemplateSet> redisTemplateSets = new HashMap<>();

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

        for(Map.Entry<String, ServiceConfig.RedisServerConfig> kv : serviceConfig.getRedisConfig2().getRedisServerConfigs().entrySet()) {
            if(kv.getValue().getType() == ServiceConfig.RedisServerConfig.RedisType.SINGLE) {
                connectionFactories.put(kv.getKey(), singleConnectionFactory(kv.getValue()));
                redisTemplateSets.put(kv.getKey(), new RedisTemplateSet());
            }
            else if(kv.getValue().getType() == ServiceConfig.RedisServerConfig.RedisType.CLUSTER) {
                connectionFactories.put(kv.getKey(), clusterConnectionFactory(kv.getValue()));
                redisTemplateSets.put(kv.getKey(), new RedisTemplateSet());
            }
        }

    }

    //TODO: thread-safe
    public ReactiveRedisTemplate<String, String> getStringReactiveTemplate(String name) {

        RedisTemplateSet set = redisTemplateSets.get(name);
        if (set == null) {
            return null;
        }

        if(set.getReactStringTemplate() == null) {
            RedisSerializer<String> serializer = new StringRedisSerializer();
            RedisSerializationContext<String, String> serializationContext = RedisSerializationContext
                    .<String, String>newSerializationContext()
                    .key(serializer)
                    .value(serializer)
                    .hashKey(serializer)
                    .hashValue(serializer)
                    .build();

            ReactiveRedisTemplate<String, String> template = new ReactiveRedisTemplate<String, String>(connectionFactories.get(name), serializationContext);
            set.setReactStringTemplate(template);
        }
        return set.getReactStringTemplate();

    }

    public ReactiveRedisTemplate<String, Object> getObjectReactiveTemplate(String name) {

        RedisTemplateSet set = redisTemplateSets.get(name);
        if (set == null) {
            return null;
        }

        if(set.getReactObjectTemplate() == null) {

            RedisSerializationContext<String, Object> serializationContext = RedisSerializationContext
                    .<String, Object>newSerializationContext(new StringRedisSerializer()).key(new StringRedisSerializer())
                    .value(new GenericJackson2JsonRedisSerializer()).build();


            ReactiveRedisTemplate<String, Object> template = new ReactiveRedisTemplate<String, Object>(connectionFactories.get(name), serializationContext);
            set.setReactObjectTemplate(template);
        }
        return set.getReactObjectTemplate();
    }

    public RedisTemplate<String, Object> getObjectTemplate(String name) {
        RedisTemplateSet set = redisTemplateSets.get(name);
        if (set == null) {
            return null;
        }
        if(set.getObjTemplate() == null) {

            RedisTemplate<String, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(connectionFactories.get(name));
            template.setDefaultSerializer(new StringRedisSerializer());
            template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
            template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
            template.afterPropertiesSet();
            set.setObjTemplate(template);
        }
        return set.getObjTemplate();

    }

    public RedisTemplate<String, String> getStringTemplate(String name) {

        RedisTemplateSet set = redisTemplateSets.get(name);
        if (set == null) {
            return null;
        }

        if(set.getStringTemplate() == null) {

            RedisTemplate<String, String> template = new RedisTemplate<String, String>();
            template.setConnectionFactory(connectionFactories.get(name));
            template.setDefaultSerializer(new StringRedisSerializer());
            template.afterPropertiesSet();
            set.setStringTemplate(template);
        }
        return set.getStringTemplate();

    }

    public RedisScript<Object> loadScript(String name, String path) {
        ScriptSource scriptSource = new ResourceScriptSource(new ClassPathResource(path));
        try {
            RedisScript<Object> script = RedisScript.of(scriptSource.getScriptAsString());
            redisScripts.put(name, script);
            return script;
        }catch(IOException ex) {
            logger.error("load {} failed", name);
            return null;
        }
    }


    public RedisScript<Object> getScript(String name) {
        return redisScripts.get(name);
    }

    public Flux<Object> runScript(String name, List<String> keyParams, List<String> argParams) {

        RedisScript<Object> script = redisScripts.get(name);
        if(script != null)
            return reactiveRedisTemplate.execute(script, keyParams, argParams);
        else {
            return null;
        }
    }

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

    private ConcurrentHashMap<String, RedisScript<Object>> redisScripts = new ConcurrentHashMap<String, RedisScript<Object>>();
}
