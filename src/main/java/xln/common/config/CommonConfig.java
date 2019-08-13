package xln.common.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
@ConfigurationProperties(prefix="xln.common.config")
@Data
public class CommonConfig
{

    //@Value("${xln-timeZone:Asia/Taipei}")
    private volatile String timeZone = "Asia/Taipei";
    private volatile String appName = "xln-app";
    private volatile boolean enablePidFileWriter = false;


}
