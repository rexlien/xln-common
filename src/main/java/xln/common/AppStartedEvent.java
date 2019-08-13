package xln.common;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import xln.common.config.CommonConfig;

@Configuration
public class AppStartedEvent implements ApplicationListener<ApplicationStartedEvent> {


    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        CommonConfig commonConfig = event.getApplicationContext().getBean(CommonConfig.class);

        if(commonConfig.isEnablePidFileWriter()) {
            ApplicationPidFileWriter fileWriter = new ApplicationPidFileWriter("./bin/" + commonConfig.getAppName() + "/shutdown.pid");
            fileWriter.setTriggerEventType(ApplicationStartedEvent.class);
            fileWriter.onApplicationEvent(event);
            event.getSpringApplication().addListeners(fileWriter);
        }

    }
}
