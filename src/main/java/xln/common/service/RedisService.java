package xln.common.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.ScriptSource;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import xln.common.config.RedisConfig;
import xln.common.config.ServiceConfig;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RedisService {

    private static Logger logger = LoggerFactory.getLogger(RedisService.class);

    @Autowired
    private ServiceConfig serviceConfig;

    @Autowired
    @Qualifier("xln-redisReactiveObjTemplate")
    private ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;


    @PostConstruct
    private void init() {
        for(ServiceConfig.RedisScript scriptConfig : serviceConfig.getRedisConfig().getScript()) {

            loadScript(scriptConfig.getName(), scriptConfig.getPath());
        }
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


    private ConcurrentHashMap<String, RedisScript<Object>> redisScripts = new ConcurrentHashMap<String, RedisScript<Object>>();
}
