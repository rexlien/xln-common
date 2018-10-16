package xln.common.config;


import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.net.URI;
import java.net.URISyntaxException;

@Slf4j
@Configuration
public class RedisConfig {



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
    ReactiveRedisTemplate<String, String> reactiveRedisTemplate(ReactiveRedisConnectionFactory factory) {
        return new ReactiveRedisTemplate<>(factory, RedisSerializationContext.string());
    }


    static Logger logger = LoggerFactory.getLogger(RedisConfig.class);

    public static LettuceConnectionFactory clusterConnectionFactory(ServiceConfig config) {

        RedisClusterConfiguration redisConfig = new RedisClusterConfiguration();

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
        return new LettuceConnectionFactory(redisConfig);
    }

    public static LettuceConnectionFactory singleConnectionFactory(ServiceConfig config) {

        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();

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

}