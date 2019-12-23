package xln.common.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class RateLimiter {

    private RedisService redisService;
    private ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;

    public RateLimiter(RedisService redisService) {
       this.reactiveRedisTemplate = redisService.getReactiveTemplate("redis-cache", Object.class);
       this.redisService = redisService;

       redisService.loadScript("_acquiredCount", "META-INF/script/acquiredCount.lua");
        redisService.loadScript("_releaseCount", "META-INF/script/releaseCount.lua");
       //redisService.loadScript("_expirablePermit", "META-INF/script/expirablePermit.lua");

    }

    @Data
    @AllArgsConstructor
    public class AcquiredInfo {
        public String key;
        public long count;

    }



    public Mono<AcquiredInfo> acquireCount(String key, long millis) {
        final var newKey = key.concat("::").concat(Long.toString(millis));
        return redisService.runScript(reactiveRedisTemplate, "_acquiredCount", List.of(newKey), List.of(millis)).next().cast(List.class).map((r)-> {
            Number result =  (Number)r.get(0);
            AcquiredInfo info = new AcquiredInfo(newKey, result.longValue());
            return info;

        });
    }

    public Mono<Long> releaseCount(String key) {

        return redisService.runScript(reactiveRedisTemplate, "_releaseCount", List.of(key), List.of(0)).next().cast(List.class).map((r)-> {
            Number result =  (Number)r.get(0);
            return result.longValue();

        });
    }

    public Mono<Long> getCount(String key, long millis) {

        final var newKey = key.concat("::").concat(Long.toString(millis));
        return reactiveRedisTemplate.opsForValue().get(newKey).cast(Number.class).map(r->{
             return r.longValue();
        }).switchIfEmpty(Mono.just(0L));
    }

    public Mono<Boolean> limit(String key, long millis, long maxRate) {

        return acquireCount(key, millis).map((r) -> {
            if(r.count > maxRate) {
                return false;
            } else {
                return true;
            }
        });
    }
}
