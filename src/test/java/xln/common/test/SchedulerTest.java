package xln.common.test;

import lombok.extern.slf4j.Slf4j;
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
import xln.common.SchedulerService;

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
    public void TestSchedule() {

        CompletableFuture<Boolean> future = new CompletableFuture<Boolean>();
            schedulerService.schedule("myjob", 500, false, ()-> {
                logger.warn("Runner runs");
                future.complete(true);

            });

            try {
                boolean res = future.get(10000, TimeUnit.MILLISECONDS);
                Assert.assertEquals(res, true);
            }catch (Exception e) {
                e.printStackTrace();
            }

    }

    @Test
    public void TestScheduleFuture()
    {
        CompletableFuture<Void> future = schedulerService.scheduleFuture("myjob", 500, ()-> {logger.warn("Future Task excuted");});
        try {
            future.thenRunAsync(()->{
                logger.warn("Then accepted");
            }).get();
        }catch(Exception e) {
            e.printStackTrace();
        }

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
