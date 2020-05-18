package xln.common.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.TemporalField;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

@Service
public class RateLimiter {

    private RedisService redisService;
    private ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;
    private static TimeZone tz = TimeZone.getTimeZone(ZoneId.systemDefault());

    public RateLimiter(RedisService redisService) {

        this.redisService = redisService;
        this.reactiveRedisTemplate = redisService.getRateLimiterServer();//getReactiveTemplate("redis-cache", Object.class);

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


    public Mono<AcquiredInfo> acquireCount(String key, long millis, boolean keyIncludeTime) {
        var newKey = key;
        if(keyIncludeTime) {
            newKey = key.concat("::").concat(Long.toString(millis));
        }
        final var finalKey = newKey;
        return redisService.runScript(reactiveRedisTemplate, "_acquiredCount", List.of(newKey), List.of(millis)).next().cast(List.class).map((r)-> {
            Number result =  (Number)r.get(0);
            AcquiredInfo info = new AcquiredInfo(finalKey, result.longValue());
            return info;

        });
    }

    //for current timezone
    public Mono<AcquiredInfo> acquireCountInInterval(String key, long interval) {

        long now = Instant.now().toEpochMilli();
        var offset = tz.getOffset(now);

        //long now = Instant.now().atZone(ZoneId.systemDefault()).getSecond() * 1000;
        long nowOffset = now + offset;
        long nextTtl = interval - (nowOffset % interval);
        return acquireCount(key, nextTtl, false);

    }


    public Mono<Long> releaseCount(String key) {

        return redisService.runScript(reactiveRedisTemplate, "_releaseCount", List.of(key), Collections.emptyList()).next().cast(List.class).map((r)-> {
            Number result =  (Number)r.get(0);
            return result.longValue();

        });
    }

    public Mono<Long> getCount(String key) {

        //final var newKey = key.concat("::").concat(Long.toString(millis));
        return reactiveRedisTemplate.opsForValue().get(key).cast(Number.class).map(r->{
             return r.longValue();
        }).switchIfEmpty(Mono.just(0L));
    }

    public Mono<Long> getTtl(String key) {
        //final var newKey = key.concat("::").concat(Long.toString(millis));
        return reactiveRedisTemplate.getExpire(key).map(r->{
            long duration = r.toMillis();
            if(duration == 0L) {
                return -1L;
            }
            return duration;
        }).switchIfEmpty(Mono.just(-1L));
    }



    public Mono<Long> deleteCount(String key) {
       // final var newKey = key.concat("::").concat(Long.toString(millis));
        return reactiveRedisTemplate.delete(key);
    }

    public Mono<Boolean> limit(String key, long millis, long maxRate) {

        return acquireCount(key, millis, true).map((r) -> {
            if(r.count > maxRate) {
                return false;
            } else {
                return true;
            }
        });
    }
}
