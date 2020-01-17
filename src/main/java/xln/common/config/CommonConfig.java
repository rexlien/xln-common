package xln.common.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
@ConfigurationProperties(prefix="xln.common.config")
public class CommonConfig
{

    public String getTimeZone() {
        return timeZone;
    }

    public CommonConfig setTimeZone(String timeZone) {
        this.timeZone = timeZone;
        return this;
    }

    public String getAppName() {
        return appName;
    }

    public CommonConfig setAppName(String appName) {
        this.appName = appName;
        return this;
    }

    public boolean isEnablePidFileWriter() {
        return enablePidFileWriter;
    }

    public CommonConfig setEnablePidFileWriter(boolean enablePidFileWriter) {
        this.enablePidFileWriter = enablePidFileWriter;
        return this;
    }

    //@Value("${xln-timeZone:Asia/Taipei}")
    private volatile String timeZone = "Asia/Taipei";
    private volatile String appName = "xln-app";
    private volatile boolean enablePidFileWriter = false;


}
