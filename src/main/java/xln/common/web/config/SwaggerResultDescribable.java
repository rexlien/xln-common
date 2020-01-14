package xln.common.web.config;

import xln.common.web.ResultDescribable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface SwaggerResultDescribable {

    Class<? extends ResultDescribable>[] clazzDescribable() default ResultDescribable.class;
    String value() default "";

}
