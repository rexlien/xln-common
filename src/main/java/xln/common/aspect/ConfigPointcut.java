package xln.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.aspectj.MethodInvocationProceedingJoinPoint;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Aspect
@Component
@Slf4j
public class ConfigPointcut {

    static private ConcurrentHashMap<Class, Consumer<ProceedingJoinPoint>> callbacks = new ConcurrentHashMap<>();

    static public void registerCallback(Class clazz, Consumer<ProceedingJoinPoint> callback) {
        callbacks.put(clazz, callback);
    }

    static private ConcurrentHashMap<Class, Consumer<ProceedingJoinPoint>> getCallbacks = new ConcurrentHashMap<>();

    static public void registeGetCallback(Class clazz, Consumer<ProceedingJoinPoint> callback) {
        getCallbacks.put(clazz, callback);
    }

    @Pointcut("execution(public * xln.common.config.*.get*(..)) && @annotation(xln.common.annotation.AspectGetter)")
    public void aspectGet() {

    }

    @Pointcut("execution(public * xln.common.config.*.set*(..)) && @annotation(xln.common.annotation.AspectSetter)")
    public void aspectSet() {

    }

    @Around("aspectGet()")
    public Object getProxy(ProceedingJoinPoint joinPoint) throws Throwable {

        /*
        //MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        //var targetClass = signature.getParameterTypes()[0];
        var callback = getCallbacks.get(signature.getReturnType());
        if(callback != null) {
            callback.accept(joinPoint);
        }

         */
        return joinPoint.proceed();
    }

    @Around("aspectSet()")
    public void setProxy(ProceedingJoinPoint joinPoint) throws Throwable {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        var targetClass = signature.getParameterTypes()[0];
        var callback = callbacks.get(targetClass);
        if(callback != null) {
            callback.accept(joinPoint);
        }

    }
}
