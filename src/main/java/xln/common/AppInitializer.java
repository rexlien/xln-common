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
                }

                ManagedChannel channel = ManagedChannelBuilder.forTarget(property).build();
                var stub = ProxyServiceGrpc.newBlockingStub(channel).withDeadlineAfter(10000, TimeUnit.MILLISECONDS);
                var builder = ProxyOuterClass.ProxyMatchRequest.newBuilder().setCluster("test");





                //builder.addProxies()

                //build()

                //stub.matchProxy()



                channel.shutdown();

            }catch (Throwable ex) {

                log.error("", ex);
            }
        });

        var property = env.getProperty("xln.common.aspect.callbacks");
        //if(property )


    }
}
