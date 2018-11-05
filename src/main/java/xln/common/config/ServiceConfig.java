package xln.common.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@ConfigurationProperties(prefix="xln.service-config")
@Validated
@Configuration("XLNServiceConfig")
@Data
public class ServiceConfig
{
    private final RedisConfig redisConfig = new RedisConfig();
    private final List<CronSchedule> cronSchedule = new ArrayList<CronSchedule>();
    private List<String> resourcePath;

    @Data
    public static class RedisConfig
    {
        private String password;
        private List<String> URI;
        private List<RedisScript> script = Collections.EMPTY_LIST;

    }

    @Configuration
    @Data
    public static class CronSchedule
    {
        String jobName;
        String jobClassName;
        String cron;
    }

    @Configuration
    @Data
    public static class RedisScript
    {
        String name;
        String path;
    }

 
}
