package xln.common.lifecycle;

import ch.qos.logback.classic.LoggerContext;
import com.github.jasync.sql.db.mysql.MySQLConnection;
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
        //init default system properties

        //for jasync driver to avoid r2dbc issue https://github.com/spring-projects/spring-data-r2dbc/issues/253
        System.setProperty(MySQLConnection.CLIENT_FOUND_ROWS_PROP_NAME, "true");

        ConfigurableEnvironment environment = event.getApplicationContext().getEnvironment();
        Properties props = new Properties();
        String strPort = environment.getProperty("server.port");

        if(strPort != null) {
            try {
                int webPort = Integer.parseInt(strPort);
                if(webPort != 0) {
                    props.put("management.server.port", (webPort + 10000) % 65536);
                } else {
                    props.put("management.server.port", 0);
                }
            }catch (NumberFormatException ex) {

            }

        } else {
            props.put("management.server.port", 18080);
        }

        environment.getPropertySources().addLast(new PropertiesPropertySource("bases-props", props));

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        Properties overrideProps = new Properties();
        var osEnv = event.getApplicationContext().getEnvironment().getSystemEnvironment();
        String name = (String)osEnv.get("XLN_APP");
        if(name != null) {
            overrideProps.put("xln.common.config.appName", name);
            loggerContext.putProperty("xln.app", name);
        } else {
            var appProperty = event.getApplicationContext().getEnvironment().getProperty("xln.common.config.appName");
            if(appProperty != null) {
                loggerContext.putProperty("xln.app", appProperty);
            }
        }

        osEnv.forEach((k, v) -> {
            if(k.startsWith("XLN_K8S")) {
                loggerContext.putProperty(k, (String)osEnv.get(k));

            }
        });

        String[] activeProfiles = environment.getActiveProfiles();
        if(activeProfiles.length > 0) {
            loggerContext.putProperty("xln.phase", activeProfiles[0]);
        }

        /*
        loggerContext.putProperty("XLN_K8S_NS", (String)osEnv.getOrDefault("XLN_K8S_NS", "null"));
        loggerContext.putProperty("XLN_K8S_POD", (String)osEnv.getOrDefault("XLN_K8S_POD", "null"));
        loggerContext.putProperty("XLN_K8S_CONT_NAME", (String)osEnv.getOrDefault("XLN_K8S_CONT_NAME", "null"));
        loggerContext.putProperty("XLN_K8S_CONT_ID", (String)osEnv.getOrDefault("XLN_K8S_CONT_ID" ,"null"));
        loggerContext.putProperty("XLN_APP", (String)osEnv.getOrDefault("XLN_APP", "null"));
*/
        boolean swaggerEnable = false;
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
        overrideProps.put("springdoc.swagger-ui.disable-swagger-default-url", "true");
        environment.getPropertySources().addFirst(new PropertiesPropertySource("override-props", overrideProps));

        for(Iterator<PropertySource<?>> it = environment.getPropertySources().iterator(); it.hasNext(); ) {
            PropertySource<?> propertySource = it.next();

            log.info(propertySource.getName(), propertySource.toString());

        }
    }
}
