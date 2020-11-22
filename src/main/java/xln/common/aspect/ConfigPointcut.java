package xln.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class ConfigPointcut {

    public class Proxy {

        public String upstream;
        public String proxy;
        public String name;


    }


    @Pointcut("execution(public * xln.common.config.*.get*(..)) && @annotation(xln.common.annotation.ProxyEndpoint)")
    public void proxyGetEndPoint() {

    }

    @Pointcut("execution(public * xln.common.config.*.set*(..)) && @annotation(xln.common.annotation.ProxyEndpoint)")
    public void proxySetEndPoint() {

    }

    @Around("proxyGetEndPoint()")
    public Object getProxy(ProceedingJoinPoint joinPoint) throws Throwable {

        return new Proxy();
    }

    @Around("proxySetEndPoint()")
    public void setProxy(ProceedingJoinPoint joinPoint) throws Throwable {

        int test = 10;
    }
}
