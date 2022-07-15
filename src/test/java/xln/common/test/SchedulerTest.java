package xln.common.test;

import lombok.extern.slf4j.Slf4j;
import org.joda.time.Instant;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import xln.common.service.SchedulerService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Slf4j
public class SchedulerTest {

    private static Logger logger = LoggerFactory.getLogger(SchedulerTest.class);
    private static final java.util.concurrent.Semaphore resultLock = new java.util.concurrent.Semaphore(0);

    @Autowired
    private SchedulerService schedulerService;

    public static class AppJob implements Job
    {
        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            logger.warn("executed");
            resultLock.release();
        }
    }

    @Test
    public void TestScheduler() throws Exception
    {
        CompletableFuture<Boolean> future = new CompletableFuture<Boolean>();
        var key = schedulerService.schedule("myjob", Instant.now().getMillis(),Instant.now().getMillis() + 10000, 1000,
                (o)-> {

            logger.warn("Future Task executed: " + o.jobState + " : " + o.intervalState );
            if(o.jobState == SchedulerService.JobState.SCHEDULED && o.intervalState == SchedulerService.IntervalState.LAST_INTERVAL) {
                future.complete(true);
                return true;
            }
            return false;
            });

        Thread.sleep(2000);
        //reschedule further
        schedulerService.reSchedule(key,Instant.now().getMillis() + 10000, Instant.now().getMillis() + 20000, 1000);

        try {
            future.get();
        }catch(Exception e) {
            e.printStackTrace();
        }

        Thread.sleep(2000);
        schedulerService.deleteJob(key);
        logger.info("Done");


    }

    @Test
    public void TestInitCron()
    {
        try {
            resultLock.acquire();
        }catch (Exception e) {
        }
    }



}
