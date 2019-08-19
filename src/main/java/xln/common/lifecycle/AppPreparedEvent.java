package xln.common.lifecycle;

import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;

import java.util.Properties;

@Configuration
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
    }
}
