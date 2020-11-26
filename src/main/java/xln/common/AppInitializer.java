package xln.common;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.boot.env.YamlPropertySourceLoader;
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AppInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

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


        ConfigPointcut.registerCallback(EndPoint.class, (joinPoint) -> {

            try {
                var endPoint = (EndPoint)joinPoint.getArgs()[0];
                var property = applicationContext.getEnvironment().getProperty("xln.toxi-proxy.host");

                if(property == null || property.equals("")) {
                    joinPoint.proceed();
                    return;
                }

                var timeout = applicationContext.getEnvironment().getProperty("xln.toxi-proxy.timeout", Long.class, -1L);

                ManagedChannel channel = ManagedChannelBuilder.forTarget(property).usePlaintext().build();
                var stub = ProxyServiceGrpc.newBlockingStub(channel);//.withDeadlineAfter(10000, TimeUnit.MILLISECONDS);
                if(timeout != -1L) {
                    stub = stub.withDeadlineAfter(timeout, TimeUnit.MILLISECONDS);
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
            }
        });

        var property = env.getProperty("xln.common.aspect.callbacks");
        //if(property )


    }
}
