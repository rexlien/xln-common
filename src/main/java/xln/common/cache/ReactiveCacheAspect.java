package xln.common.cache;


import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import xln.common.annotation.ReactiveCacheable;
import xln.common.service.CacheService;

@Aspect
@Component
@Slf4j
public class ReactiveCacheAspect {

    @Autowired
    private CacheService cacheService;

    @Autowired
    private CacheExpressionEvaluator cacheExpressionEvaluator;


    @Pointcut("execution(public * *(..)) && @annotation(xln.common.annotation.ReactiveCacheable)")
    public void cacheablePointcut() {

    }


    @Around("cacheablePointcut()")
    public Object getCache(ProceedingJoinPoint joinPoint) throws Throwable{

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        if(signature.getReturnType().isAssignableFrom(Mono.class)) {

            ReactiveCacheable reactiveCacheable = signature.getMethod().getAnnotation(ReactiveCacheable.class);
            Cache cache = cacheService.getCache(reactiveCacheable.cacheManagerID(), reactiveCacheable.cacheName());
            if(cache != null) {

                EvaluationContext context = cacheExpressionEvaluator.createContext(joinPoint.getTarget(), ((MethodSignature) joinPoint.getSignature()).getMethod(), joinPoint.getArgs());
                String key = (String)cacheExpressionEvaluator.evaluate(reactiveCacheable.cacheKey(), new AnnotatedElementKey(signature.getMethod(), signature.getClass()), context);

                Cache.ValueWrapper ret = cache.get(key);
                Mono mono = (Mono)ret.get();
                return mono.switchIfEmpty(Mono.defer(() -> {
                    try {
                        return ((Mono)(joinPoint.proceed())).flatMap((r)->{
                            if(cache instanceof ReactiveCache) {
                                return ((ReactiveCache) cache).putReactive(key, r);
                            } else {
                                cache.put(key, r);
                            }
                            return Mono.just(r);
                        });
                    }catch (Throwable ex) {
                        log.error("", ex);
                    }
                    return Mono.empty();
                }
                ));

            }
            return Mono.empty();
        }
        else {
            throw new RuntimeException("return type not supported");
        }

    }

}
