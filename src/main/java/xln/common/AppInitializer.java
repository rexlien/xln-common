package xln.common;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.support.ResourcePropertySource;

@Slf4j
public class AppInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static Logger logger = LoggerFactory.getLogger(AppInitializer.class);

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
            env.getPropertySources().addFirst(new ResourcePropertySource("classpath:xln-common" + suffix + ".properties"));
        }catch (Exception e)
        {

            logger.error("XLN Property Load failed");
        }
    }
}