package xln.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@ConfigurationProperties(prefix="xln.scheduler-config")
@Validated
@Configuration
@Data
public class SchedulerConfig
{
    @Data
    public static class CronSchedule
    {
        private String jobName;
        private String jobClassName;
        private String cron;
    }

    private boolean enable = false;
    private int threadCount = 1;
    private List<CronSchedule> cronSchedule = new LinkedList<>();
}
