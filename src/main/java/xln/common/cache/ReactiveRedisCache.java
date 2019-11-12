package xln.common.cache;

import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.cache.support.NullValue;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Mono;

import java.util.concurrent.Callable;

public class ReactiveRedisCache extends AbstractValueAdaptingCache implements ReactiveCache {



    private ReactiveRedisTemplate<String, Object> template;
    private String name;
    private RedisCacheConfiguration cacheConfig;

    public ReactiveRedisCache(String name, ReactiveRedisTemplate<String, Object> template, RedisCacheConfiguration cacheConfig, boolean allowNullValue) {
        super(allowNullValue);
        this.name = name;
        this.template = template;
        this.cacheConfig = cacheConfig;
    }


    @Override
    protected Object lookup(Object key) {

        return template.opsForValue().get(key);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return template;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper result = get(key);

        if (result != null) {
            return (T) result.get();
        }

        T value = valueFromLoader(key, valueLoader);
        put(key, value);
        return value;

    }


    @Override
    public void put(Object key, Object value) {

        value = preProcessCacheValue(value);
        if(!isAllowNullValues() && value == null) {
            throw new IllegalArgumentException(String.format(
                    "Cache '%s' does not allow 'null' values. Avoid storing null via '@Cacheable(unless=\"#result == null\")' or configure RedisCache to allow 'null' via RedisCacheConfiguration.",
                    name));
        }
        template.opsForValue().set((String)key, value, cacheConfig.getTtl()).subscribe();
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {

        value = preProcessCacheValue(value);

        if(!isAllowNullValues() && value == null) {
            return new SimpleValueWrapper(Mono.just(false));
        }
        Mono<Boolean> res = template.opsForValue().setIfAbsent((String)key, value, cacheConfig.getTtl());

        return new SimpleValueWrapper(res);
    }

    @Override
    public void evict(Object key) {

    }

    @Override
    public boolean evictIfPresent(Object key) {
        return false;
    }

    @Override
    public void clear() {

    }

    @Override
    public boolean invalidate() {
        return false;
    }

    @Nullable
    protected Object preProcessCacheValue(@Nullable Object value) {

        if (value != null) {
            return value;
        }

        return isAllowNullValues() ? NullValue.INSTANCE : null;
    }

    private static <T> T valueFromLoader(Object key, Callable<T> valueLoader) {

        try {
            return valueLoader.call();
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }

    @Override
    public Mono<Object> putReactive(Object key, Object value) {


        //if(!isAllowNullValues() && nullableValue == null) {
        //    return Mono.error(new IllegalArgumentException(String.format(
        //            "Cache '%s' does not allow 'null' values. Avoid storing null via '@Cacheable(unless=\"#result == null\")' or configure RedisCache to allow 'null' via RedisCacheConfiguration.",
         //           name)));
        //
        //}
        if(isAllowNullValues()){
            final Object nullableValue = preProcessCacheValue(value);
            return template.opsForValue().set((String)key, nullableValue, cacheConfig.getTtl()).flatMap((r)->{
                //return value no matter successful or fail
                if(value == null) {
                    return Mono.empty();
                }
                return Mono.just(value);
            });
        } else {
            if(value == null) {
                return Mono.empty();
            } else {
                return template.opsForValue().set((String)key, value, cacheConfig.getTtl()).map((r)->{
                    //return value no matter successful or fail
                    return value;
                });
            }
        }


    }

    public Mono<Object> getReactive(Object key) {
        return template.opsForValue().get(key).flatMap((r) -> {
            if(r instanceof NullValue) {
                return Mono.empty();
            } else {
                return Mono.just(r);
            }
        });
    }

}
