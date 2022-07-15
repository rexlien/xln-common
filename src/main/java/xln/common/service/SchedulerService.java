package xln.common.service;


import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.encoder.org.apache.commons.lang3.mutable.Mutable;
import org.quartz.*;
import org.quartz.impl.triggers.SimpleTriggerImpl;
import org.quartz.spi.MutableTrigger;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import xln.common.config.SchedulerConfig;
import xln.common.config.ServiceConfig;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import java.sql.Date;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

@Service
@Slf4j
@ConditionalOnProperty(prefix = "xln.scheduler-config", name = "enable", havingValue = "true")
public class SchedulerService {

    private final SchedulerConfig config;

    private final ConfigurableBeanFactory beanFactory;

    private Scheduler scheduler;

    SchedulerService(SchedulerConfig config, ConfigurableBeanFactory beanFactory) {
        this.config = config;
        this.beanFactory = beanFactory;
    }

    public enum JobState {

        //SCHEDULING,
        SCHEDULED,
        //SCHEDULED_FIRST_FIRED,
        UNSCHEDULED,

    }

    public enum IntervalState {
        FIRST_INTERVAL,
        INTERVAL,
        LAST_INTERVAL

    }

    public static class RunningState {
        public JobState jobState;
        public IntervalState intervalState;


        public RunningState(JobState jobState, IntervalState intervalState) {
            this.jobState = jobState;
            this.intervalState = intervalState;
        }
    }
/*
    public static class ScheduleFuture<T> extends CompletableFuture<T> {

        private JobKey jobKey;
        private Scheduler scheduler;

        public ScheduleFuture(Scheduler scheduler, JobKey key) {
            this.scheduler = scheduler;
            this.jobKey = key;
        }

        public void cancel() {
            try {
                this.scheduler.deleteJob(jobKey);
            } catch (Exception e) {
                e.printStackTrace();
            }
            this.cancel(true);
        }

    }
*/
    private static class ServiceJob implements Job {

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {

            var runner = (Function<RunningState, Boolean>) context.getMergedJobDataMap().get("runner");
            if (runner != null) {
                boolean quit = false;

                //first interval if it's the first fire of current job, or if it's been reschedule and the scudeule time is same as the new trigger time
                if (context.getPreviousFireTime() == null || context.getScheduledFireTime() == context.getTrigger().getStartTime()) {
                    quit = runner.apply(new RunningState(JobState.SCHEDULED, IntervalState.FIRST_INTERVAL));
                } else if (context.getNextFireTime() == null) {
                    quit = runner.apply(new RunningState(JobState.SCHEDULED, IntervalState.LAST_INTERVAL));
                } else {
                    quit = runner.apply(new RunningState(JobState.SCHEDULED, IntervalState.INTERVAL));
                }

                if (quit) {
                    try {
                        context.getScheduler().unscheduleJob(context.getTrigger().getKey());
                        //context.getScheduler().deleteJob(context.getJobDetail().getKey());
                    } catch (Exception ex) {
                        log.error("", ex);
                    }
                }
            }

        }
    }

    public class MySchedulerListener implements SchedulerListener {

        private Scheduler scheduler;

        public MySchedulerListener(Scheduler scheduler) {
            this.scheduler = scheduler;
        }

        @Override
        public void jobScheduled(Trigger trigger) {
            log.info("job scheduled");
        }

        @Override
        public void jobUnscheduled(TriggerKey triggerKey) {
            log.info("job unscheduled");
            try {
                var objKey = scheduler.getTrigger(triggerKey).getJobKey();
                var runner = (Function<RunningState, Boolean>) scheduler.getJobDetail(objKey).getJobDataMap().get("runner");
                if (runner != null) {
                    runner.apply(new RunningState(JobState.UNSCHEDULED, IntervalState.INTERVAL));
                }
            } catch (SchedulerException ex) {
                log.error("", ex);
            }
        }

        @Override
        public void triggerFinalized(Trigger trigger) {

            log.info("trigger finalized");
            /*
            try {
                var runner = (Function<RunningState, Boolean>) scheduler.getJobDetail(trigger.getJobKey()).getJobDataMap().get("runner");
                if (runner != null) {
                    runner.apply(new RunningState(JobState.UNSCHEDULED, IntervalState.LAST_INTERVAL));
                }
            }catch (SchedulerException ex) {
                log.error("", ex);
            }
*/
        }

        @Override
        public void triggerPaused(TriggerKey triggerKey) {

        }

        @Override
        public void triggersPaused(String triggerGroup) {

        }

        @Override
        public void triggerResumed(TriggerKey triggerKey) {

        }

        @Override
        public void triggersResumed(String triggerGroup) {

        }

        @Override
        public void jobAdded(JobDetail jobDetail) {

        }

        @Override
        public void jobDeleted(JobKey jobKey) {

        }

        @Override
        public void jobPaused(JobKey jobKey) {

        }

        @Override
        public void jobsPaused(String jobGroup) {

        }

        @Override
        public void jobResumed(JobKey jobKey) {

        }

        @Override
        public void jobsResumed(String jobGroup) {

        }

        @Override
        public void schedulerError(String msg, SchedulerException cause) {

        }

        @Override
        public void schedulerInStandbyMode() {

        }

        @Override
        public void schedulerStarted() {

        }

        @Override
        public void schedulerStarting() {

        }

        @Override
        public void schedulerShutdown() {

        }

        @Override
        public void schedulerShuttingdown() {

        }

        @Override
        public void schedulingDataCleared() {

        }
    }


    @PostConstruct
    void init() throws SchedulerException {
        //if(!config.isEnable())
        //  return;

        SchedulerFactoryBean bean = new SchedulerFactoryBean();
        Properties prop = new Properties();
        prop.setProperty("org.quartz.threadPool.threadCount", String.valueOf(config.getThreadCount()));
        bean.setQuartzProperties(prop);
        try {
            bean.afterPropertiesSet();
        } catch (Exception e) {
            log.error("scheduler bean create failed", e);
            return;
        }
        beanFactory.registerSingleton("xln-schedulerFactory", bean);
        //bean = beanFactory.getBean(SchedulerFactoryBean.class);
        scheduler = bean.getScheduler();
        scheduler.getListenerManager().addSchedulerListener(new MySchedulerListener(scheduler));
        try {
            scheduler.start();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        //schedule configured cron
        for (SchedulerConfig.CronSchedule schedule : config.getCronSchedule()) {
            try {
                schedule(schedule.getJobName(), schedule.getJobClassName(), schedule.getCron());
            } catch (ClassNotFoundException ex) {
                ex.printStackTrace();
            }
        }

    }

    @PreDestroy
    void destroy() {
        try {
            scheduler.clear();
        }catch (SchedulerException ex) {
            log.error("", ex);
        }
    }

    /*
        public <T extends Job> void schedule(Class<T> clazz, String jobName, long interval, boolean repeaat) {

            JobBuilder.newJob(clazz)
                    .withIdentity(jobName, "XLN")
                    .storeDurably(true)
                    .build();

        }
    */
    private JobDetail createJob(String jobName) {

        JobDetail job = JobBuilder.newJob().ofType(ServiceJob.class)
                .withIdentity(jobName, "XLN")
                .storeDurably(true)
                .build();
        return job;
    }
/*
    public JobKey schedule(String jobName, long interval, boolean repeat, Supplier<Boolean> runner) {

        JobDetail job = createJob(jobName);
        job.getJobDataMap().put("runner", runner);

        SimpleScheduleBuilder builder = simpleSchedule().withIntervalInMilliseconds(interval);

        if(repeat) {
            builder.repeatForever();
        }

        Trigger trigger = TriggerBuilder.newTrigger().forJob(job).withSchedule(builder).build();
        try {
            scheduler.scheduleJob(job, trigger);
        }catch (Exception e) {
            e.printStackTrace();
        }

        return job.getKey();
    }
*/

    public JobKey schedule(String jobName, String cron, Function<Object, Boolean> runner) {

        JobDetail job = createJob(jobName);
        job.getJobDataMap().put("runner", runner);

        CronTrigger trigger = TriggerBuilder.newTrigger().forJob(job).withSchedule(cronSchedule(cron)).build();
        try {
            scheduler.scheduleJob(job, trigger);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return job.getKey();
    }

    public JobKey schedule(String jobName, String jobClassName, String cron) throws ClassNotFoundException {


        Class cls = Class.forName(jobClassName);

        JobDetail job = JobBuilder.newJob(cls)
                .withIdentity(jobName, "XLN")
                .storeDurably(true)
                .build();
        CronTrigger trigger = TriggerBuilder.newTrigger().forJob(job).withSchedule(cronSchedule(cron)).build();
        try {
            scheduler.scheduleJob(job, trigger);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return job.getKey();
    }


    /*
        public ScheduleFuture<Void> scheduleFuture(String jobName, long interval, Runner runner)
        {
            JobDetail job = JobBuilder.newJob().ofType(ServiceJob.class)
                    .withIdentity(jobName, "XLN")
                    .storeDurably(true)
                    .build();
            job.getJobDataMap().put("runner", runner);

            SimpleScheduleBuilder builder = simpleSchedule().withIntervalInMilliseconds(interval);

            ScheduleFuture<Void> future = new ScheduleFuture<Void>(scheduler, job.getKey());
            job.getJobDataMap().put("future", future);
            Trigger trigger = TriggerBuilder.newTrigger().forJob(job).withSchedule(builder).build();
            try {
                scheduler.scheduleJob(job, trigger);
            }catch (Exception e) {
                e.printStackTrace();
            }
            return future;

        }
    */
    public JobKey schedule(String jobName, long startAt, long endAt, long interval, Function<RunningState, Boolean> runner) {
        JobDetail job = JobBuilder.newJob().ofType(ServiceJob.class)
                .withIdentity(jobName, "XLN")
                .build();
        job.getJobDataMap().put("runner", runner);

        SimpleScheduleBuilder builder = null;
        if (interval != -1) {
            builder = simpleSchedule().withIntervalInMilliseconds(interval).repeatForever();
            job.getJobDataMap().put("longRunning", true);
        }
        //ScheduleFuture<Void> future = new ScheduleFuture<Void>(scheduler, job.getKey());
        //job.getJobDataMap().put("future", future);
        var triggerBuilder = TriggerBuilder.newTrigger().forJob(job).startAt(Date.from(Instant.ofEpochMilli(startAt)));
        if (endAt != -1) {
            triggerBuilder.endAt(Date.from(Instant.ofEpochMilli(endAt)));

        }
        if (builder != null) {
            triggerBuilder.withSchedule(builder);
        }
        Trigger trigger = triggerBuilder.build();
        //trigger.getTriggerBuilder().build()
        try {
            scheduler.scheduleJob(job, trigger);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return job.getKey();
    }

    public void reSchedule(JobKey jobKey, long startAt, long endAt, long interval) throws SchedulerException {

        var triggers = scheduler.getTriggersOfJob(jobKey);
        for (var trigger : triggers) {

            var mutableTrigger = (MutableTrigger) trigger;

            if (startAt >= mutableTrigger.getEndTime().getTime()) {
                mutableTrigger.setEndTime(Date.from(Instant.ofEpochMilli(endAt)));
                mutableTrigger.setStartTime(Date.from(Instant.ofEpochMilli(startAt)));
            } else {
                mutableTrigger.setStartTime(Date.from(Instant.ofEpochMilli(startAt)));
                mutableTrigger.setEndTime(Date.from(Instant.ofEpochMilli(endAt)));

            }
            scheduler.rescheduleJob(trigger.getKey(), mutableTrigger);
        }

    }

    public void deleteJob(JobKey jobKey) {
        try {
            scheduler.deleteJob(jobKey);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
