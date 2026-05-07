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
import xln.common.dist.OneTimeTaskHandler
import xln.common.dist.ScheduledTaskHandler
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
@Import(EtcdTaskCoordinatorTest.OneTimeHandler::class, EtcdTaskCoordinatorTest.ScheduledHandler::class, EtcdTaskCoordinatorTest.ProgressStateHandler::class, EtcdTaskCoordinatorTest.SlowHandler::class)
@ActiveProfiles("etcd-coordinator-test")
class EtcdTaskCoordinatorTest {

    @Autowired
    private val dTaskService: DTaskService? = null

    @Autowired
    private val etcdClient: EtcdClient? = null

    @Autowired
    private val oneTimeHandler: OneTimeHandler? = null

    @Autowired
    private val scheduledHandler: ScheduledHandler? = null

    @Autowired
    private val progressStateHandler: ProgressStateHandler? = null

    @Autowired
    private val slowHandler: SlowHandler? = null

    companion object {
        val logger = KotlinLogging.logger {}

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

    // Handler for one-time dispatch tests. waitForTask() returns a future resolved when handle() is called.
    @Component
    class OneTimeHandler : OneTimeTaskHandler() {
        val handleCount = AtomicInteger(0)
        private val pending = ConcurrentHashMap<String, CompletableFuture<String>>()

        fun waitForTask(taskId: String): CompletableFuture<String> =
            pending.computeIfAbsent(taskId) { CompletableFuture() }

        override suspend fun handle(dTask: DTask) {
            val count = handleCount.incrementAndGet()
            log.info("OneTimeHandler.handle called (count=$count) for task ${dTask.id}")
            pending[dTask.id]?.complete(dTask.id)
        }

        override fun serviceFilters(): List<Pair<String, String>> =
            listOf(Pair("coordinator-test-group", "coordinator-test-service"))
    }

    // Handler for scheduled task tests. nextResult controls whether handle() returns true (keep) or false (delete early).
    @Component
    class ScheduledHandler : ScheduledTaskHandler() {
        private val pending = ConcurrentHashMap<String, CompletableFuture<String>>()
        private val endPending = ConcurrentHashMap<String, CompletableFuture<String>>()
        var nextResult: Boolean = true

        fun waitForHandle(taskId: String): CompletableFuture<String> =
            pending.computeIfAbsent(taskId) { CompletableFuture() }

        fun waitForEnd(taskId: String): CompletableFuture<String> =
            endPending.computeIfAbsent(taskId) { CompletableFuture() }

        override suspend fun handle(dTask: DTask): Boolean {
            log.info("ScheduledHandler.handle called for task ${dTask.id}")
            pending[dTask.id]?.complete(dTask.id)
            return nextResult
        }

        override suspend fun handleEnd(dTask: DTask) {
            log.info("ScheduledHandler.handleEnd called for task ${dTask.id}")
            endPending[dTask.id]?.complete(dTask.id)
        }

        override fun serviceFilters(): List<Pair<String, String>> =
            listOf(Pair("coordinator-test-group", "coordinator-test-service"))

        override fun handleRate(): Long = 3_000L
    }

    // Simulates a handler that reads task+progress state from etcd and writes updated state on each tick,
    // then calls versionDeleteTask() on the next tick once state is written (mirrors SmartChannelService pattern:
    // getTask → getProgressState → setProgressState / deleteProgressState → versionDeleteTask when done).
    // Uses a distinct service pair so the coordinator registers a separate watcher without conflicting with ScheduledHandler.
    @Component
    class ProgressStateHandler(private val dTaskService: DTaskService) : ScheduledTaskHandler() {
        companion object {
            const val SCH_GROUP = "coordinator-test-group"
            const val SCH_SERVICE = "coordinator-sch-service"
        }

        private val handlePending = ConcurrentHashMap<String, CompletableFuture<String>>()
        private val endPending   = ConcurrentHashMap<String, CompletableFuture<String>>()
        // Track per-task whether the first tick has already written progress state
        private val stateWritten = ConcurrentHashMap<String, Boolean>()

        fun waitForHandle(taskId: String): CompletableFuture<String> =
            handlePending.computeIfAbsent(taskId) { CompletableFuture() }

        fun waitForEnd(taskId: String): CompletableFuture<String> =
            endPending.computeIfAbsent(taskId) { CompletableFuture() }

        // Tick 1: getTask → getProgressState → setProgressState (state not yet written).
        // Tick 2: getTask → getProgressState → deleteProgressState → versionDeleteTask (desired state met).
        // If task was externally deleted before any tick: getTask() returns null, noop, return true.
        // versionDeleteTask() returns false (CAS miss) if task already gone — must not throw.
        override suspend fun handle(dTask: DTask): Boolean {
            log.info("ProgressStateHandler.handle called for task ${dTask.id}")
            val task = dTaskService.getTask(SCH_GROUP, SCH_SERVICE, dTask.id)
            if (task == null) {
                log.info("ProgressStateHandler: task ${dTask.id} already gone, noop")
                handlePending.computeIfAbsent(dTask.id) { CompletableFuture() }.complete(dTask.id)
                return true
            }
            val alreadyWritten = stateWritten[dTask.id] == true
            if (!alreadyWritten) {
                // First tick: write progress state
                dTaskService.setProgressState(SCH_GROUP, SCH_SERVICE, task, "main", DTask.getDefaultInstance())
                stateWritten[dTask.id] = true
            } else {
                // Second tick: desired state met — delete progress state and version-delete the task
                dTaskService.deleteProgressState(SCH_GROUP, SCH_SERVICE, dTask.id)
                val deleted = dTaskService.versionDeleteTask(SCH_GROUP, SCH_SERVICE, task)
                log.info("ProgressStateHandler: versionDeleteTask result=$deleted for ${dTask.id}")
            }
            handlePending.computeIfAbsent(dTask.id) { CompletableFuture() }.complete(dTask.id)
            return true
        }

        override suspend fun handleEnd(dTask: DTask) {
            log.info("ProgressStateHandler.handleEnd called for task ${dTask.id}")
            // Cleanup on task end: delete progress state. If task was already version-deleted by handle() or
            // externally cancelled, the progress key may already be gone — deleteProgressState is idempotent.
            dTaskService.deleteProgressState(SCH_GROUP, SCH_SERVICE, dTask.id)
            stateWritten.remove(dTask.id)
            endPending.computeIfAbsent(dTask.id) { CompletableFuture() }.complete(dTask.id)
        }

        override fun serviceFilters(): List<Pair<String, String>> =
            listOf(Pair(SCH_GROUP, SCH_SERVICE))

        override fun handleRate(): Long = 3_000L
    }

    // Handler whose handle() sleeps longer than handleRate() to verify the coordinator never runs
    // concurrent ticks for the same task.
    @Component
    class SlowHandler : ScheduledTaskHandler() {
        companion object {
            const val SLOW_GROUP = "coordinator-test-group"
            const val SLOW_SERVICE = "coordinator-slow-service"
            // handle() sleeps 5 s; handleRate() is 2 s — every tick would overlap without the guard
            const val HANDLE_SLEEP_MS = 5_000L
        }

        val concurrentCount = AtomicInteger(0)
        val peakConcurrency = AtomicInteger(0)
        val handleCount = AtomicInteger(0)
        private val firstHandleFuture = CompletableFuture<String>()

        fun waitForFirstHandle(taskId: String): CompletableFuture<String> = firstHandleFuture

        override suspend fun handle(dTask: DTask): Boolean {
            val concurrent = concurrentCount.incrementAndGet()
            peakConcurrency.updateAndGet { max -> maxOf(max, concurrent) }
            log.info("SlowHandler.handle start (concurrent=$concurrent) for ${dTask.id}")
            delay(HANDLE_SLEEP_MS)
            val count = handleCount.incrementAndGet()
            log.info("SlowHandler.handle end (count=$count) for ${dTask.id}")
            concurrentCount.decrementAndGet()
            firstHandleFuture.complete(dTask.id)
            return true
        }

        override fun serviceFilters(): List<Pair<String, String>> =
            listOf(Pair(SLOW_GROUP, SLOW_SERVICE))

        override fun handleRate(): Long = 2_000L
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

            val leaseId1 = client.leaseManager.createOrGetLease(0, 30, true, 15000).awaitSingle().response.id
            val leaseId2 = client.leaseManager.createOrGetLease(0, 30, true, 15000).awaitSingle().response.id

            val results = listOf(
                async { svc.claimTask(group, service, taskId, leaseId1) },
                async { svc.claimTask(group, service, taskId, leaseId2) }
            ).awaitAll()

            val successCount = results.count { it }
            assertEquals("Exactly one claim must succeed", 1, successCount)

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

    // A scheduled task (with ScheduleConfig) must be routed to the scheduled handler, not the one-time handler.
    @Test
    fun testScheduledTaskSkippedByCoordinator() {
        runBlocking {
            val svc = dTaskService!!
            val group = "coordinator-test-group"
            val service = "coordinator-test-service"
            val taskId = "scheduled-skip-${Instant.now().toEpochMilli()}"
            val now = Instant.now().toEpochMilli()

            val countBefore = oneTimeHandler!!.handleCount.get()

            val res = svc.scheduleTask(group, service, taskId,
                DTaskService.ScheduleTaskParam(now, now + 60_000))
            assertEquals(0, res.result)

            // Wait briefly — one-time handler must not fire for scheduled tasks
            delay(3_000)

            assertEquals("coordinator must not handle scheduled tasks via one-time handler",
                countBefore, oneTimeHandler.handleCount.get())

            // Task must still be in etcd (scheduled path owns its lifecycle)
            val task = svc.getTask(group, service, taskId)
            assertNotNull("scheduled task must still exist in etcd", task)

            svc.cancelTask(group, service, taskId)
        }
    }

    // The unified coordinator claims a scheduled task and fires handle() via the tick loop.
    // start=0, end=1 → already past end, so coordinator deletes after first handle().
    @Test
    fun testScheduledTaskHandledByCoordinator() {
        runBlocking {
            val svc = dTaskService!!
            val group = "coordinator-test-group"
            val service = "coordinator-test-service"
            val taskId = "scheduled-handle-${Instant.now().toEpochMilli()}"

            val future = scheduledHandler!!.waitForHandle(taskId)

            val res = svc.scheduleTask(group, service, taskId,
                DTaskService.ScheduleTaskParam(0, 1))
            assertEquals(0, res.result)

            // handleRate() = 3 s, wait up to 10 s
            val handledId = future.get(10, TimeUnit.SECONDS)
            assertEquals(taskId, handledId)

            var task = svc.getTask(group, service, taskId)
            val deadline = System.currentTimeMillis() + 5_000
            while (task != null && System.currentTimeMillis() < deadline) {
                delay(100)
                task = svc.getTask(group, service, taskId)
            }
            assertNull("scheduled task must be deleted after end", task)
        }
    }

    // EtcdTaskCoordinator dispatches a one-time task (no ScheduleConfig) to the registered handler,
    // then deletes the task.
    @Test
    fun testOneTimeTaskDispatch() {
        runBlocking {
            val svc = dTaskService!!
            val group = "coordinator-test-group"
            val service = "coordinator-test-service"
            val taskId = "one-time-${Instant.now().toEpochMilli()}"

            val future = oneTimeHandler!!.waitForTask(taskId)

            val res = svc.allocateTask(group, service, taskId, DTaskService.AllocateTaskParam())
            assertEquals(0, res.result)

            val handledId = future.get(10, TimeUnit.SECONDS)
            assertEquals(taskId, handledId)

            var task = svc.getTask(group, service, taskId)
            val deadline = System.currentTimeMillis() + 5000
            while (task != null && System.currentTimeMillis() < deadline) {
                delay(100)
                task = svc.getTask(group, service, taskId)
            }
            assertNull("one-time task must be deleted after dispatch", task)
        }
    }

    // When handle() returns false the coordinator must delete the task and call handleEnd().
    @Test
    fun testScheduledTaskEarlyDelete() {
        runBlocking {
            val svc = dTaskService!!
            val group = "coordinator-test-group"
            val service = "coordinator-test-service"
            val taskId = "early-delete-${Instant.now().toEpochMilli()}"
            val now = Instant.now().toEpochMilli()

            scheduledHandler!!.nextResult = false
            val handleFuture = scheduledHandler.waitForHandle(taskId)
            val endFuture = scheduledHandler.waitForEnd(taskId)

            val res = svc.scheduleTask(group, service, taskId,
                DTaskService.ScheduleTaskParam(now, now + 600_000))
            assertEquals(0, res.result)

            handleFuture.get(10, TimeUnit.SECONDS)
            val endId = endFuture.get(5, TimeUnit.SECONDS)
            assertEquals(taskId, endId)

            var task = svc.getTask(group, service, taskId)
            val deadline = System.currentTimeMillis() + 5_000
            while (task != null && System.currentTimeMillis() < deadline) {
                delay(100)
                task = svc.getTask(group, service, taskId)
            }
            assertNull("task must be deleted when handle() returns false", task)

            scheduledHandler.nextResult = true
        }
    }

    // While the coordinator holds a scheduled task in its tick loop, an external cancelTask() must trigger
    // handleEnd() via the DELETE watch event and clean up the scheduledTaskMap entry.
    @Test
    fun testScheduledTaskExternalDeleteTriggersHandleEnd() {
        runBlocking {
            val svc = dTaskService!!
            val group = "coordinator-test-group"
            val service = "coordinator-test-service"
            val taskId = "ext-delete-${Instant.now().toEpochMilli()}"
            val now = Instant.now().toEpochMilli()

            val handleFuture = scheduledHandler!!.waitForHandle(taskId)
            val endFuture = scheduledHandler.waitForEnd(taskId)

            // Far-future end so coordinator never auto-deletes
            val res = svc.scheduleTask(group, service, taskId,
                DTaskService.ScheduleTaskParam(now, now + 600_000))
            assertEquals(0, res.result)

            // Wait for at least one tick so the coordinator has claimed and called handle()
            handleFuture.get(10, TimeUnit.SECONDS)

            // External delete — coordinator must notice via DELETE watch and call handleEnd()
            svc.cancelTask(group, service, taskId)

            val endId = endFuture.get(10, TimeUnit.SECONDS)
            assertEquals(taskId, endId)

            assertNull("externally deleted task must not exist in etcd",
                svc.getTask(group, service, taskId))
        }
    }

    // Handler that reads task state and writes progress on each tick (mirrors real-world service pattern).
    // When the task is externally deleted mid-flight: getTask() returns null (safe noop), handleEnd() is still
    // called by the coordinator via DELETE watch and must safely call deleteProgressState() on a gone key.
    @Test
    fun testProgressStateHandlerExternalDelete() {
        runBlocking {
            val svc = dTaskService!!
            val group = ProgressStateHandler.SCH_GROUP
            val service = ProgressStateHandler.SCH_SERVICE
            val taskId = "psh-ext-delete-${Instant.now().toEpochMilli()}"
            val now = Instant.now().toEpochMilli()

            val handleFuture = progressStateHandler!!.waitForHandle(taskId)
            val endFuture = progressStateHandler.waitForEnd(taskId)

            // Far-future end so coordinator never auto-deletes
            val res = svc.scheduleTask(group, service, taskId,
                DTaskService.ScheduleTaskParam(now, now + 600_000))
            assertEquals(0, res.result)

            // Wait until at least one handle() tick writes progress state
            handleFuture.get(10, TimeUnit.SECONDS)

            // External delete while the handler may still be mid-tick
            svc.cancelTask(group, service, taskId)

            // Coordinator must call handleEnd() via DELETE watch — deleteProgressState() in handleEnd() must not throw
            val endId = endFuture.get(10, TimeUnit.SECONDS)
            assertEquals(taskId, endId)

            assertNull("externally deleted task must not exist in etcd", svc.getTask(group, service, taskId))
            assertNull("progress state must not exist after task deletion",
                svc.getProgressState(group, service, taskId))
        }
    }

    // handle() sleeps 5 s while handleRate() is 2 s — without the activeTickJobs guard, every tick would
    // launch a new concurrent execution. Verifies peak concurrency stays at 1 across multiple tick intervals.
    @Test
    fun testSlowHandlerNoConcurrentTicks() {
        runBlocking {
            val svc = dTaskService!!
            val group = SlowHandler.SLOW_GROUP
            val service = SlowHandler.SLOW_SERVICE
            val taskId = "slow-handle-${Instant.now().toEpochMilli()}"
            val now = Instant.now().toEpochMilli()

            val res = svc.scheduleTask(group, service, taskId,
                DTaskService.ScheduleTaskParam(now, now + 600_000))
            assertEquals(0, res.result)

            // Wait for at least one full handle() cycle to complete (> HANDLE_SLEEP_MS + a few ticks worth)
            slowHandler!!.waitForFirstHandle(taskId).get(15, TimeUnit.SECONDS)

            // Let a few more tick intervals pass to give any concurrent launch a chance to show up
            delay(SlowHandler.HANDLE_SLEEP_MS + 3_000)

            assertEquals("handle() must never run concurrently for the same task",
                1, slowHandler.peakConcurrency.get())

            svc.cancelTask(group, service, taskId)
        }
    }

    // Two pods racing to claim the same scheduled task — exactly one must win.
    @Test
    fun testScheduledTaskClaimExclusivity() {
        runBlocking {
            val svc = dTaskService!!
            val client = etcdClient!!
            val group = "claim-test-group"
            val service = "claim-test-service"
            val taskId = "sched-race-${Instant.now().toEpochMilli()}"

            val leaseId1 = client.leaseManager.createOrGetLease(0, 30, true, 15000).awaitSingle().response.id
            val leaseId2 = client.leaseManager.createOrGetLease(0, 30, true, 15000).awaitSingle().response.id

            val results = listOf(
                async { svc.claimTask(group, service, taskId, leaseId1) },
                async { svc.claimTask(group, service, taskId, leaseId2) }
            ).awaitAll()

            val successCount = results.count { it }
            assertEquals("Exactly one scheduled claim must succeed", 1, successCount)

            svc.releaseTaskClaim(group, service, taskId)
            svc.cancelTask(group, service, taskId)
        }
    }
}
