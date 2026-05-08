package xln.common.test

import com.google.protobuf.ByteString
import etcdserverpb.KVGrpc
import etcdserverpb.Rpc
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.assertj.core.util.Lists
import org.junit.Assert.assertEquals
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
import xln.common.dist.HandleResult
import xln.common.dist.OneTimeTaskHandler
import xln.common.dist.ScheduledTaskHandler
import xln.common.etcd.DTaskService
import xln.common.proto.task.DTaskOuterClass.DTask
import xln.common.proto.task.DTaskOuterClass.ScheduleConfig
import xln.common.test.container.EtcdContainer
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger {}

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [TestApplication::class])
@Import(
    EtcdTaskCoordinatorStartupTest.StartupOneTimeHandler::class,
    EtcdTaskCoordinatorStartupTest.StartupScheduledHandler::class
)
@ActiveProfiles("etcd-coordinator-startup-test")
class EtcdTaskCoordinatorStartupTest {

    @Autowired
    private val dTaskService: DTaskService? = null

    @Autowired
    private val startupOneTimeHandler: StartupOneTimeHandler? = null

    @Autowired
    private val startupScheduledHandler: StartupScheduledHandler? = null

    companion object {
        private val logger = KotlinLogging.logger {}

        const val SERVICE_GROUP = "startup-test-group"
        const val SERVICE_NAME = "startup-test-service"
        const val DTASK_ROOT = "xln-dtask-startup-test"

        val network: Network = Network.newNetwork()

        val etcd = EtcdContainer(network, object : EtcdContainer.LifecycleListener {
            override fun started(container: EtcdContainer?) { logger.info("startup-test etcd started") }
            override fun failedToStart(container: EtcdContainer?, exception: Exception?) { logger.error("startup-test etcd failed", exception) }
            override fun stopped(container: EtcdContainer?) { logger.info("startup-test etcd stopped") }
        }, false, "startup-etcd", "startup-etcd", Lists.emptyList(), false)

        val preOneTimeTaskId = "pre-one-time-${System.currentTimeMillis()}"
        val preScheduledTaskId = "pre-scheduled-${System.currentTimeMillis()}"

        @JvmStatic
        @DynamicPropertySource
        fun dynamicProperties(registry: DynamicPropertyRegistry) {
            etcd.start()
            val port = etcd.container.firstMappedPort
            waitForPort("127.0.0.1", port)
            registry.add("xln.etcd-config.endPoint.hosts[0]") { "127.0.0.1:$port" }

            // Write tasks via raw gRPC before Spring context starts so they are picked up via beforeStartWatch replay
            val channel = ManagedChannelBuilder.forAddress("127.0.0.1", port).usePlaintext().build()
            val kvClient = KVGrpc.newBlockingStub(channel)

            val oneTimeTask = DTask.newBuilder()
                .setId(preOneTimeTaskId)
                .setCreateTime(Instant.now().toEpochMilli())
                .build()
            kvClient.put(Rpc.PutRequest.newBuilder()
                .setKey(ByteString.copyFromUtf8("$DTASK_ROOT.$SERVICE_GROUP.$SERVICE_NAME.tasks.$preOneTimeTaskId"))
                .setValue(oneTimeTask.toByteString())
                .build())

            // start=0, end=1 → already past end, triggers deletion on first tick after handle()
            val scheduledTask = DTask.newBuilder()
                .setId(preScheduledTaskId)
                .setCreateTime(Instant.now().toEpochMilli())
                .setScheduleConfig(ScheduleConfig.newBuilder().setStart(0).setEnd(1))
                .build()
            kvClient.put(Rpc.PutRequest.newBuilder()
                .setKey(ByteString.copyFromUtf8("$DTASK_ROOT.$SERVICE_GROUP.$SERVICE_NAME.tasks.$preScheduledTaskId"))
                .setValue(scheduledTask.toByteString())
                .build())

            channel.shutdown()
            logger.info("Pre-populated etcd: one-time=$preOneTimeTaskId, scheduled=$preScheduledTaskId")
        }
    }

    @Component
    class StartupOneTimeHandler : OneTimeTaskHandler() {
        // Pre-initialize futures for the known task IDs so handle() can complete them even before waitForTask() is called
        private val pending = ConcurrentHashMap<String, CompletableFuture<String>>().also {
            it[preOneTimeTaskId] = CompletableFuture()
        }

        fun waitForTask(taskId: String): CompletableFuture<String> =
            pending.computeIfAbsent(taskId) { CompletableFuture() }

        override suspend fun handle(dTask: DTask) {
            log.info("StartupOneTimeHandler.handle: ${dTask.id}")
            pending.computeIfAbsent(dTask.id) { CompletableFuture() }.complete(dTask.id)
        }

        override fun serviceFilters() = listOf(Pair(SERVICE_GROUP, SERVICE_NAME))
    }

    @Component
    class StartupScheduledHandler : ScheduledTaskHandler() {
        // Pre-initialize futures for the known task IDs so handle() can complete them even before waitForHandle() is called
        private val pending = ConcurrentHashMap<String, CompletableFuture<String>>().also {
            it[preScheduledTaskId] = CompletableFuture()
        }

        fun waitForHandle(taskId: String): CompletableFuture<String> =
            pending.computeIfAbsent(taskId) { CompletableFuture() }

        override suspend fun handle(dTask: DTask): HandleResult {
            log.info("StartupScheduledHandler.handle: ${dTask.id}")
            pending.computeIfAbsent(dTask.id) { CompletableFuture() }.complete(dTask.id)
            return HandleResult.CONTINUE
        }

        override fun serviceFilters() = listOf(Pair(SERVICE_GROUP, SERVICE_NAME))
        override fun handleRate(): Long = 3_000L
    }

    // Tasks written to etcd before the Spring context starts must be picked up via watchServiceTask's
    // beforeStartWatch replay (not a live PUT event). Verifies the one-time task is dispatched and deleted.
    @Test
    fun testPreExistingOneTimeTaskPickedUpOnStartup() {
        val future = startupOneTimeHandler!!.waitForTask(preOneTimeTaskId)
        val handledId = future.get(15, TimeUnit.SECONDS)
        assertEquals(preOneTimeTaskId, handledId)

        runBlocking {
            var task = dTaskService!!.getTask(SERVICE_GROUP, SERVICE_NAME, preOneTimeTaskId)
            val deadline = System.currentTimeMillis() + 5_000
            while (task != null && System.currentTimeMillis() < deadline) {
                delay(100)
                task = dTaskService.getTask(SERVICE_GROUP, SERVICE_NAME, preOneTimeTaskId)
            }
            assertNull("pre-existing one-time task must be deleted after dispatch", task)
        }
    }

    // Same as above for a scheduled task. start=0, end=1 → already past end, so the coordinator
    // claims it on replay, fires handle() on the first tick, then deletes it.
    @Test
    fun testPreExistingScheduledTaskPickedUpOnStartup() {
        val future = startupScheduledHandler!!.waitForHandle(preScheduledTaskId)
        val handledId = future.get(15, TimeUnit.SECONDS)
        assertEquals(preScheduledTaskId, handledId)

        runBlocking {
            var task = dTaskService!!.getTask(SERVICE_GROUP, SERVICE_NAME, preScheduledTaskId)
            val deadline = System.currentTimeMillis() + 5_000
            while (task != null && System.currentTimeMillis() < deadline) {
                delay(100)
                task = dTaskService.getTask(SERVICE_GROUP, SERVICE_NAME, preScheduledTaskId)
            }
            assertNull("pre-existing scheduled task past end must be deleted", task)
        }
    }
}
