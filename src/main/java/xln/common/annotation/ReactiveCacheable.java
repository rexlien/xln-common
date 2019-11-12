package xln.common.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface ReactiveCacheable {

    String cacheManagerID() default "";
    String cacheName() default "";
    String cacheKey() default "";
}
