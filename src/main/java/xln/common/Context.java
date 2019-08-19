package xln.common;


import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import xln.common.config.ServiceConfig;

import javax.annotation.PostConstruct;
import java.util.List;


@Configuration
@Slf4j
public class Context implements ApplicationContextAware {


    private static final String DEV_PROFILE = "dev";
    private static final String LOCAL_PROFILE = "local";
    private static final String TEST_PROFILE = "test";

    @Autowired
    private GenericApplicationContext context;

    @Autowired
    private ServiceConfig serviceConfig;

    private volatile ApplicationContext curContext;
    private volatile boolean isDev = false;
    private volatile boolean isTest = false;
    private volatile String phase = "";

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        curContext = applicationContext;

        String[] profiles = curContext.getEnvironment().getActiveProfiles();

        for(String profile : profiles) {

            if (profile.equals(DEV_PROFILE) || profile.equals(LOCAL_PROFILE)) {
                isDev = true;
            } else if(profile.equals(TEST_PROFILE)) {
                isTest = true;
            }
        }

        if(profiles.length > 0) {
            phase = profiles[0];

        }

    }




    private static Logger logger = LoggerFactory.getLogger(Context.class);

    @PostConstruct
    void postConstruct()
    {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(context.getClassLoader());
        try {

            List<String> paths = serviceConfig.getResourcePath();
            for(String resPath : paths) {
                Resource[] resources = resolver.getResources(resPath);
                for (Resource res : resources) {
                    XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(context);
                    reader.loadBeanDefinitions(res);

                }
            }
        }catch(Exception e) {
            logger.error("Resource Load failed", e);
        }

    }

    public boolean profilesContain(String profileName) {

        String[] profiles = curContext.getEnvironment().getActiveProfiles();

        for(String profile : profiles) {

            if (profile.equals(profileName)) {
                return true;
            }
        }
        return false;
    }

    public String getPhase() {
        return phase;
    }

    public boolean isDevEnv() {
        return isDev;
    }

    public boolean isTestEnv() {
        return isTest;
    }

}
