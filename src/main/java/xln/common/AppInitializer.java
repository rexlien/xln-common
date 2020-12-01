package xln.common;

import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePropertySource;
import xln.common.annotation.ProxyEndpoint;
import xln.common.aspect.ConfigPointcut;
import xln.common.config.CommonConfig;
import xln.common.proto.proxypb.ProxyOuterClass;
import xln.common.proto.proxypb.ProxyServiceGrpc;
import xln.common.proxy.EndPoint;
import xln.common.proxy.EndPointProvider;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AppInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {


    private static void processEndPoint(ApplicationContext applicationContext, ProceedingJoinPoint joinPoint, EndPoint endPoint) {

        ManagedChannel channel = null;
        try {

            var property = applicationContext.getEnvironment().getProperty("xln.toxi-proxy.host");

            if(property == null || property.equals("")) {
                joinPoint.proceed();
                return;
            }

            var timeout = applicationContext.getEnvironment().getProperty("xln.toxi-proxy.timeout", Long.class, -1L);
            var waitReady = applicationContext.getEnvironment().getProperty("xln.toxi-proxy.waitReady", Boolean.class, true);

            channel = ManagedChannelBuilder.forTarget(property).usePlaintext().build();
            var stub = ProxyServiceGrpc.newBlockingStub(channel);
            if(timeout != -1L) {
                stub = stub.withDeadlineAfter(timeout, TimeUnit.MILLISECONDS);
            }

            if(waitReady) {
                stub = stub.withWaitForReady();
            }
            var proxyBuilder = ProxyOuterClass.Proxy.newBuilder().setName(endPoint.name);
            for(var host : endPoint.hosts) {
                proxyBuilder.addUpStreamHosts(host);
            }

            //match and replace endpoint
            var matched = stub.matchProxy(ProxyOuterClass.ProxyMatchRequest.newBuilder().setCluster("cluster").addProxies(proxyBuilder.build()).build());
            var matchedProxiesList = matched.getProxiesList();
            if(matchedProxiesList.size() > 0) {
                var proxy = matchedProxiesList.get(0);

                endPoint.name = proxy.getName();
                endPoint.setHosts(proxy.getUpStreamHostsList());
            }
            channel.shutdown();
            joinPoint.proceed();

        }catch (Throwable ex) {

            log.warn("Reset toxi configs failed,  toxi environment won't work", ex);
        } finally {
            if(channel != null) {
                channel.shutdown();
            }
        }
    }


    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {


        String[] profiles = applicationContext.getEnvironment().getActiveProfiles();
        String suffix = "";
        if(profiles.length > 0)
        {
            //suffix = "-" + profiles[0];
        }

        ConfigurableEnvironment env = applicationContext.getEnvironment();
        try {
            Resource resource = applicationContext.getResource("classpath:xln-common" + suffix + ".yml");
            YamlPropertySourceLoader sourceLoader = new YamlPropertySourceLoader();
            List<PropertySource<?>> yml = sourceLoader.load("xln-common",resource);
            for(PropertySource<?> propertySource : yml) {
                env.getPropertySources().addLast(propertySource);
            }
            //env.getPropertySources().addFirst(new ResourcePropertySource("classpath:xln-common" + suffix + ".properties"));
        }catch (Exception e) {

            log.warn("XLN Property Load failed");
        }

        Class<Map<Object,EndPointProvider>> mapClazz =
                (Class<Map<Object,EndPointProvider>>)(Class)Map.class;
        ConfigPointcut.registerCallback(mapClazz, (joinPoint) -> {

            var endPoints = (Map<Object,EndPointProvider>)joinPoint.getArgs()[0];
            for(var endPoint : endPoints.entrySet()) {
                processEndPoint(applicationContext, joinPoint, endPoint.getValue().getEndPoint());
            }

        });

        ConfigPointcut.registerCallback(EndPoint.class, (joinPoint) -> {


            var endPoint = (EndPoint)joinPoint.getArgs()[0];

            processEndPoint(applicationContext, joinPoint, endPoint);
        });

        var property = env.getProperty("xln.common.aspect.callbacks");
        //if(property )


    }
}
