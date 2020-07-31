package xln.common.lifecycle;

import ch.qos.logback.classic.LoggerContext;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.Iterator;
import java.util.Properties;

@Configuration
@Slf4j
public class AppPreparedEvent implements ApplicationListener<ApplicationPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationPreparedEvent event) {
        ConfigurableEnvironment environment = event.getApplicationContext().getEnvironment();
        Properties props = new Properties();
        String strPort = environment.getProperty("server.port");

        if(strPort != null) {
            try {
                int webPort = Integer.parseInt(strPort);
                props.put("management.server.port", (webPort + 10000) % 65536);
            }catch (NumberFormatException ex) {

            }

        } else {
            props.put("management.server.port", 18080);
        }

        environment.getPropertySources().addLast(new PropertiesPropertySource("bases-props", props));

        Properties overrideProps = new Properties();
        String name = System.getenv("XLN_APP");
        if(name != null) {
            overrideProps.put("xln.common.config.appName", name);
        }

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        var osEnv = event.getApplicationContext().getEnvironment().getSystemEnvironment();
        osEnv.forEach((k, v) -> {
            if(k.startsWith("XLN")) {
                loggerContext.putProperty(k, (String)osEnv.get(k));

            }
        });
        /*
        loggerContext.putProperty("XLN_K8S_NS", (String)osEnv.getOrDefault("XLN_K8S_NS", "null"));
        loggerContext.putProperty("XLN_K8S_POD", (String)osEnv.getOrDefault("XLN_K8S_POD", "null"));
        loggerContext.putProperty("XLN_K8S_CONT_NAME", (String)osEnv.getOrDefault("XLN_K8S_CONT_NAME", "null"));
        loggerContext.putProperty("XLN_K8S_CONT_ID", (String)osEnv.getOrDefault("XLN_K8S_CONT_ID" ,"null"));
        loggerContext.putProperty("XLN_APP", (String)osEnv.getOrDefault("XLN_APP", "null"));
*/
        boolean swaggerEnable = false;
        String[] activeProfiles = environment.getActiveProfiles();
        for(var activeProfile : activeProfiles) {
            if(activeProfile.equals("xln-swagger2-webflux") || activeProfile.equals("xln-swagger-enable")) {
               swaggerEnable = true;
               break;
            }

        }
        if(!swaggerEnable) {
            overrideProps.put("springdoc.api-docs.enabled", "false");
            overrideProps.put("springdoc.swagger-ui.enabled", "false");
        }
        environment.getPropertySources().addFirst(new PropertiesPropertySource("override-props", overrideProps));

        for(Iterator<PropertySource<?>> it = environment.getPropertySources().iterator(); it.hasNext(); ) {
            PropertySource<?> propertySource = it.next();

            log.info(propertySource.getName(), propertySource.toString());

        }
    }
}
