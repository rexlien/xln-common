package xln.common.test

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.assertj.core.util.Lists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Component
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit4.SpringRunner
import org.testcontainers.containers.Network
import xln.common.dist.DTaskScheduler
import xln.common.etcd.DTaskService
import xln.common.proto.task.DTaskOuterClass.DTask
import xln.common.service.EtcdClient
import xln.common.test.container.EtcdContainer
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private val log = KotlinLogging.logger {}

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [TestApplication::class])
@Import(TaskCoordinatorTest.OneTimeHandler::class)
@ActiveProfiles("coordinator-test")
class TaskCoordinatorTest {

    @Autowired
    private val dTaskService: DTaskService? = null

    @Autowired
    private val etcdClient: EtcdClient? = null

    @Autowired
    private val oneTimeHandler: OneTimeHandler? = null

    companion object {
        val logger = KotlinLogging.logger {}

        // Separate network to avoid alias conflicts with UtilTestKt's shared etcd container
        val network: Network = Network.newNetwork()

        val etcd = EtcdContainer(network, object : EtcdContainer.LifecycleListener {
            override fun started(container: EtcdContainer?) { logger.info("etcd started") }
            override fun failedToStart(container: EtcdContainer?, exception: Exception?) { logger.error("etcd failed", exception) }
            override fun stopped(container: EtcdContainer?) { logger.info("etcd stopped") }
        }, false, "coordinator-etcd", "coordinator-etcd", Lists.emptyList(), false)

        @JvmStatic
        @DynamicPropertySource
        fun dynamicProperties(registry: DynamicPropertyRegistry) {
            etcd.start()
            val port = etcd.container.firstMappedPort
            waitForPort("127.0.0.1", port)
            registry.add("xln.etcd-config.endPoint.hosts[0]") { "127.0.0.1:$port" }
        }
    }

    // Handler registered with TaskCoordinatorService for one-time dispatch tests.
    // waitForTask() registers a per-taskId future so tests can wait for specific tasks.
    @Component
    class OneTimeHandler : DTaskScheduler.Handler() {
        val handleCount = AtomicInteger(0)
        private val pending = ConcurrentHashMap<String, CompletableFuture<String>>()

        fun waitForTask(taskId: String): CompletableFuture<String> =
            pending.computeIfAbsent(taskId) { CompletableFuture() }

        override suspend fun handle(dTask: DTask): Boolean {
            val count = handleCount.incrementAndGet()
            log.info("OneTimeHandler.handle called (count=$count) for task ${dTask.id}")
            pending[dTask.id]?.complete(dTask.id)
            return true
        }

        override fun serviceFilters(): List<Pair<String, String>> =
            listOf(Pair("coordinator-test-group", "coordinator-test-service"))
    }

    // Two coroutines race to claimTask with different lease IDs — exactly one must succeed.
    @Test
    fun testClaimTaskExclusivity() {
        runBlocking {
            val svc = dTaskService!!
            val client = etcdClient!!
            val group = "claim-test-group"
            val service = "claim-test-service"
            val taskId = "race-task-${Instant.now().toEpochMilli()}"

            // Create two separate leases simulating two competing pods
            val leaseId1 = client.leaseManager.createOrGetLease(0, 30, true, 15000).awaitSingle().response.id
            val leaseId2 = client.leaseManager.createOrGetLease(0, 30, true, 15000).awaitSingle().response.id

            val results = listOf(
                async { svc.claimTask(group, service, taskId, leaseId1) },
                async { svc.claimTask(group, service, taskId, leaseId2) }
            ).awaitAll()

            val successCount = results.count { it }
            assertEquals("Exactly one claim must succeed", 1, successCount)

            // cleanup
            svc.releaseTaskClaim(group, service, taskId)
        }
    }

    // allocateTask + progress + setProgressState → getTaskSummary returns all three
    @Test
    fun testGetTaskSummary() {
        runBlocking {
            val svc = dTaskService!!
            val group = "summary-test-group"
            val service = "summary-test-service"

            val res = svc.allocateTask(group, service, DTaskService.AllocateTaskParam())
            assertEquals(0, res.result)

            svc.progress(group, service, res.taskId, 1, 100)
            val task = svc.getTask(group, service, res.taskId)!!
            svc.setProgressState(group, service, task, "main", DTask.getDefaultInstance())

            val summary = svc.getTaskSummary(group, service, res.taskId)
            assertNotNull("summary must not be null", summary)
            assertNotNull("task must be present", summary!!.task)
            assertNotNull("progress must be present", summary.progress)
            assertNotNull("state must be present", summary.state)
            assertEquals(res.taskId, summary.task!!.value.id)
        }
    }

    // TaskCoordinatorService dispatches a one-time task (no ScheduleConfig) to the registered handler,
    // then deletes the task. This replaces the old SmartChannelConsumerApp Kafka consumer scenario.
    @Test
    fun testOneTimeTaskDispatch() {
        runBlocking {
            val svc = dTaskService!!
            val group = "coordinator-test-group"
            val service = "coordinator-test-service"
            val taskId = "one-time-${Instant.now().toEpochMilli()}"

            // Register the future BEFORE allocating the task to avoid a race
            val future = oneTimeHandler!!.waitForTask(taskId)

            val res = svc.allocateTask(group, service, taskId, DTaskService.AllocateTaskParam())
            assertEquals(0, res.result)

            // TaskCoordinatorService watches etcd for PUT events and dispatches — wait up to 10 s
            val handledId = future.get(10, TimeUnit.SECONDS)
            assertEquals(taskId, handledId)

            // versionDeleteTask runs after handle() returns; poll until deletion is visible or timeout
            var task = svc.getTask(group, service, taskId)
            val deadline = System.currentTimeMillis() + 5000
            while (task != null && System.currentTimeMillis() < deadline) {
                delay(100)
                task = svc.getTask(group, service, taskId)
            }
            assertNull("one-time task must be deleted after dispatch", task)
        }
    }
}
