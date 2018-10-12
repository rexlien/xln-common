package xln.common.config;

import com.google.common.util.concurrent.AbstractScheduledService;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

@Configuration
public class QuartzConfig {

    @Bean(name="xln-QuartzScheduler", destroyMethod = "shutdown")
    Scheduler quartzScheduler(@Qualifier("xln-QuartzSchedulerBean") SchedulerFactoryBean bean)
    {
        return bean.getScheduler();
    }

    @Bean(name="xln-QuartzSchedulerBean")
    SchedulerFactoryBean quartzScheulderBean()
    {
       SchedulerFactoryBean bean = new SchedulerFactoryBean();

        SchedulerFactoryBean schedulerFactory = new SchedulerFactoryBean();
        schedulerFactory.setConfigLocation(new ClassPathResource("quartz.properties"));

        //schedulerFactory.setJobFactory(springBeanJobFactory());

       return bean;
    }
}
