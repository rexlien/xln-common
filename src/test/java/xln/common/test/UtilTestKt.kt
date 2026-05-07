package xln.common.test

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.assertj.core.util.Lists
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Component
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit4.SpringRunner
import org.testcontainers.containers.Network
import xln.common.dist.ScheduledTaskHandler
import xln.common.etcd.DTaskService
import xln.common.proto.task.DTaskOuterClass
import xln.common.proto.task.DTaskOuterClass.DTask
import xln.common.test.container.EtcdContainer
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger {}

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [TestApplication::class])
@Import(UtilTestKt.TestHandler::class)
@ActiveProfiles("dtask-test")
class UtilTestKt {

    @Autowired
    private lateinit var dTaskService: DTaskService

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    companion object {

        val network: Network = Network.newNetwork()

        val etcd = EtcdContainer(
            network,
            object : EtcdContainer.LifecycleListener {
                override fun started(c: EtcdContainer?) { log.info("UtilTestKt: etcd started") }
                override fun failedToStart(c: EtcdContainer?, e: Exception?) { log.error("UtilTestKt: etcd failed", e) }
                override fun stopped(c: EtcdContainer?) { log.info("UtilTestKt: etcd stopped") }
            },
            false, "dtask-etcd", "dtask-etcd", Lists.emptyList(), false
        )

        @JvmStatic
        @DynamicPropertySource
        fun dynamicProperties(registry: DynamicPropertyRegistry) {
            etcd.start()
            val port = etcd.container.firstMappedPort
            waitForPort("127.0.0.1", port)
            registry.add("xln.etcd-config.endPoint.hosts[0]") { "127.0.0.1:$port" }
        }
    }

    @Component
    class TestHandler : ScheduledTaskHandler() {
        val forceFinishFuture = CompletableFuture<Boolean>()
        val endTestFuture = CompletableFuture<Boolean>()

        override suspend fun handle(dTask: DTask): Boolean {
            log.info("handle: ${dTask.id}")
            return true
        }

        override suspend fun handleEnd(dTask: DTask) {
            log.info("handleEnd: ${dTask.id}")
            if (dTask.id == "finishedTask") forceFinishFuture.complete(true)
            else endTestFuture.complete(true)
        }

        override fun serviceFilters() = listOf(Pair("watch-service-group", "my-schedule-service"))

        override fun handleRate() = 3000L
    }

    @Test
    fun testDtaskService() {
        runBlocking {
            val res = dTaskService.allocateTask(
                "my-service-group", "my-service",
                DTaskService.AllocateTaskParam()
            )
            Assert.assertEquals(0, res.result)

            val taskMap = dTaskService.listTasks("my-service-group", "my-service", 0)
            Assert.assertTrue(taskMap.contains(res.taskPath))
            taskMap.forEach { _, u -> Assert.assertFalse(u.value.hasScheduleConfig()) }

            var progressRes = dTaskService.progress("my-service-group", "my-service", res.taskId, 0, 100)
            Assert.assertTrue(progressRes.succeeded)

            var progress = dTaskService.getProgress("my-service-group", "my-service", res.taskId)
            Assert.assertEquals(0, progress!!.curProgress)

            progressRes = dTaskService.progress("my-service-group", "my-service", res.taskId, 1, 100)
            Assert.assertTrue(progressRes.succeeded)

            progress = dTaskService.getProgress("my-service-group", "my-service", res.taskId)
            Assert.assertEquals(1, progress!!.curProgress)
        }
    }

    @Test
    fun testScheduleDTaskCreateAndCancel() {
        runBlocking {
            val res = dTaskService.scheduleTask(
                "my-service-group", "my-schedule-service", "testId",
                DTaskService.ScheduleTaskParam(0, 0)
            )
            Assert.assertEquals(0, res.result)

            dTaskService.progress("my-service-group", "my-schedule-service", "testId", 0, 100)
            dTaskService.setProgressState(
                "my-service-group", "my-schedule-service", res.task!!,
                "testKey", DTaskOuterClass.DTask.getDefaultInstance()
            )

            dTaskService.cancelTask("my-service-group", "my-schedule-service", res.taskId)

            Assert.assertNull(dTaskService.getTask("my-service-group", "my-schedule-service", res.taskId))
            Assert.assertNull(dTaskService.getProgressState("my-service-group", "my-schedule-service", res.taskId))
            Assert.assertNull(dTaskService.getProgress("my-service-group", "my-schedule-service", "testId"))
        }
    }

    @Test
    fun testDTaskHandler() {
        val handler = applicationContext.getBean(TestHandler::class.java)
        runBlocking {
            dTaskService.scheduleTask(
                "watch-service-group", "my-schedule-service", "testId",
                DTaskService.ScheduleTaskParam(Instant.now().toEpochMilli(), Instant.now().toEpochMilli() + 10000)
            )
            dTaskService.scheduleTask(
                "watch-service-group", "my-schedule-service", "finishedTask",
                DTaskService.ScheduleTaskParam(Instant.now().toEpochMilli(), Instant.now().toEpochMilli() + 999999999)
            )
        }

        handler.endTestFuture.get(30, TimeUnit.SECONDS)
        val succeeded = runBlocking {
            dTaskService.cancelTask("watch-service-group", "my-schedule-service", "finishedTask")
        }
        Assert.assertTrue(succeeded)
        handler.forceFinishFuture.get(30, TimeUnit.SECONDS)

        Thread.sleep(2000)
        runBlocking {
            Assert.assertNull(dTaskService.getTask("watch-service-group", "my-schedule-service", "testId"))
        }
    }
}
