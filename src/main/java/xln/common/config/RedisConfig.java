package xln.common.config;


import io.lettuce.core.ReadFrom;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.*;
import org.springframework.scripting.ScriptSource;
import org.springframework.scripting.support.ResourceScriptSource;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;


@Slf4j
@Configuration
public class RedisConfig {

/*
    @Bean(name="xln-redisClusterConnection", destroyMethod = "close")
    RedisClusterConnection redisClusterConnection(@Qualifier("redisConnectionFactory") LettuceConnectionFactory factory) {
        if(!factory.isClusterAware())
            return null;

        return factory.getClusterConnection();
    }

    @Bean(name="xln-redisSingleConnection", destroyMethod = "close")
    RedisConnection redisSingleConnection(@Qualifier("redisConnectionFactory") LettuceConnectionFactory factory) {

        return factory.getConnection();
    }
*/
    @Bean(name="xln-redisStringTemplate")
    RedisTemplate<String, String> redisStringTemplate(@Qualifier("redisConnectionFactory") LettuceConnectionFactory factory)
    {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setDefaultSerializer(new StringRedisSerializer());
        return template;
    }

    @Bean(name="xln-redisObjectTemplate")
    RedisTemplate<String, Object> redisObjectTemplate(@Qualifier("redisConnectionFactory") LettuceConnectionFactory factory)
    {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }

    @Bean(name="xln-redisReactiveStringTemplate")
    ReactiveRedisTemplate<String, String> reactiveRedisTemplate(@Qualifier("redisConnectionFactory") LettuceConnectionFactory factory) {

        RedisSerializer<String> serializer = new StringRedisSerializer();
        RedisSerializationContext<String , String> serializationContext = RedisSerializationContext
                .<String, String>newSerializationContext()
                .key(serializer)
                .value(serializer)
                .hashKey(serializer)
                .hashValue(serializer)
                .build();

        ReactiveRedisTemplate<String, String> template =  new ReactiveRedisTemplate<String, String>(factory, serializationContext);
        return template;

    }

    @Bean(name="xln-redisReactiveObjTemplate")
    ReactiveRedisTemplate<String, Object> reactiveRedisObjTemplate(@Qualifier("redisConnectionFactory") LettuceConnectionFactory factory) {
        RedisSerializationContext<String, Object> serializationContext = RedisSerializationContext
                .<String, Object>newSerializationContext(new StringRedisSerializer()).key(new StringRedisSerializer())
                .value(new GenericJackson2JsonRedisSerializer()).build();

        ReactiveRedisTemplate<String, Object> template =  new ReactiveRedisTemplate<String, Object>(factory, serializationContext);

        return template;

    }

    /*
    @Bean(name="xln-redisReactiveListTemplate")
    ReactiveRedisTemplate<String, List> reactiveRedisListTemplate(@Qualifier("redisConnectionFactory") ReactiveRedisConnectionFactory factory) {

        //RedisSerializationContext<String, List<Long>> serializationContext = RedisSerializationContext//
        //        .<String, List<Long>>newSerializationContext(new StringRedisSerializer()).value(new Red).build();

        //ReactiveRedisTemplate<String, List<Long>> template =  new ReactiveRedisTemplate<String, List<Long>>(factory, serializationContext);

        return template;

    }
*/

    static Logger logger = LoggerFactory.getLogger(RedisConfig.class);

    public static LettuceConnectionFactory clusterConnectionFactory(ServiceConfig config) {


        RedisClusterConfiguration redisConfig = new RedisClusterConfiguration();
        redisConfig.setPassword(config.getRedisConfig().getPassword());
        for(String s : config.getRedisConfig().getURI()) {

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
        if(config.getRedisConfig().isSlaveRead()) {
            LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                    .readFrom(ReadFrom.SLAVE)
                    .build();
            return new LettuceConnectionFactory(redisConfig, clientConfig);
        }
        return new LettuceConnectionFactory(redisConfig);
    }

    public static LettuceConnectionFactory singleConnectionFactory(ServiceConfig config) {

        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setPassword(config.getRedisConfig().getPassword());
        for(String s : config.getRedisConfig().getURI()) {
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
        return new LettuceConnectionFactory(redisConfig);
    }

    public static LettuceConnectionFactory sentinelConnectionFactory(ServiceConfig config) {

        RedisSentinelConfiguration redisConfig = new RedisSentinelConfiguration().master("xlnMaster");
        redisConfig.setPassword(config.getRedisConfig().getPassword());
        for(String s : config.getRedisConfig().getURI()) {

            URI uri = null;
            try {
                uri = new URI(s);
            }catch(URISyntaxException ex) {
                logger.error(ex.toString());
            }
            if(uri != null) {

                redisConfig.sentinel(uri.getHost(), uri.getPort());


            }
        }
        return new LettuceConnectionFactory(redisConfig);
    }

}