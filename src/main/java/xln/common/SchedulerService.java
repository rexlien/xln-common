package xln.common;


import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import xln.common.config.ServiceConfig;

import javax.annotation.PostConstruct;

import java.util.concurrent.CompletableFuture;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

@Service
public class SchedulerService {


    @Autowired
    @Qualifier("xln-QuartzScheduler")
    Scheduler scheduler;


    @Autowired
    ServiceConfig config;

    public interface Runner
    {
        void run();
    }

    public static class ScheduleFuture<T> extends CompletableFuture<T>
    {

        private JobKey jobKey;
        private Scheduler scheduler;

        public ScheduleFuture(Scheduler scheduler, JobKey key)
        {
            this.scheduler = scheduler;
            this.jobKey = key;
        }

        public void cancel()
        {
            try {
                this.scheduler.deleteJob(jobKey);
            }catch(Exception e) {
                e.printStackTrace();
            }
            this.complete(null);
        }

    }

    private static class ServiceJob implements Job {

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {

            Runner runner = (Runner)context.getMergedJobDataMap().get("runner");
            if(runner != null)
                runner.run();

            CompletableFuture<Void> future = (CompletableFuture<Void>)context.getMergedJobDataMap().get("future");
            if(future != null) {
                future.complete(null);
            }


        }
    }


    @PostConstruct
    void init()
    {
        try {
            scheduler.start();
        }catch(Exception e) {
            e.printStackTrace();
            return;
        }

        //schedule configured cron
        for(ServiceConfig.CronSchedule schedule : config.getCronSchedule()) {
            try {
                schedule(schedule.getJobName(), schedule.getJobClassName(), schedule.getCron());
            }catch(ClassNotFoundException ex) {
                ex.printStackTrace();
            }
        }

    }


    public <T extends Job> void schedule(Class<T> clazz, String jobName, long interval, boolean repeaat) {

        JobBuilder.newJob(clazz)
                .withIdentity(jobName, "XLN")
                .storeDurably(true)
                .build();

    }

    private JobDetail createJob(String jobName) {

        JobDetail job = JobBuilder.newJob().ofType(ServiceJob.class)
                .withIdentity(jobName, "XLN")
                .storeDurably(true)
                .build();
        return job;
    }

    public JobKey schedule(String jobName, long interval, boolean repeat, Runner runner) {

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


    public JobKey schedule(String jobName, String cron, Runner runner) {

        JobDetail job = createJob(jobName);
        job.getJobDataMap().put("runner", runner);

        CronTrigger trigger = TriggerBuilder.newTrigger().forJob(job).withSchedule(cronSchedule(cron)).build();
        try {
            scheduler.scheduleJob(job, trigger);
        }catch (Exception e) {
            e.printStackTrace();
        }

        return job.getKey();
    }

    public JobKey schedule(String jobName, String jobClassName, String cron) throws ClassNotFoundException{


        Class cls = Class.forName(jobClassName);

        JobDetail job = JobBuilder.newJob(cls)
                .withIdentity(jobName, "XLN")
                .storeDurably(true)
                .build();
        CronTrigger trigger = TriggerBuilder.newTrigger().forJob(job).withSchedule(cronSchedule(cron)).build();
        try {
            scheduler.scheduleJob(job, trigger);
        }catch (Exception e) {
            e.printStackTrace();
        }

        return job.getKey();
    }



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


    public void deleteJob(JobKey jobKey) {
        try {
            scheduler.deleteJob(jobKey);
        }catch(Exception e) {
            e.printStackTrace();
        }

    }






}
