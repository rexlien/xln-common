package xln.common.test

import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import mvccpb.Kv
import org.assertj.core.util.Lists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit4.SpringRunner
import org.testcontainers.containers.Network
import xln.common.dist.HandleResult
import xln.common.dist.ScheduledTaskHandler
import xln.common.etcd.*
import xln.common.proto.task.DTaskOuterClass.DTask
import xln.common.service.EtcdClient
import xln.common.test.container.EtcdContainer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger {}

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [TestApplication::class])
@Import(EtcdWatchTest.NoOpHandler::class)
@ActiveProfiles("etcd-watch-test")
class EtcdWatchTest {

    companion object {
        val network: Network = Network.newNetwork()

        val etcd = EtcdContainer(
            network,
            object : EtcdContainer.LifecycleListener {
                override fun started(c: EtcdContainer?) { log.info("EtcdWatchTest: etcd started") }
                override fun failedToStart(c: EtcdContainer?, e: Exception?) { log.error("EtcdWatchTest: etcd failed", e) }
                override fun stopped(c: EtcdContainer?) { log.info("EtcdWatchTest: etcd stopped") }
            },
            false, "watch-etcd", "watch-etcd", Lists.emptyList(), true
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

    @org.springframework.stereotype.Component
    class NoOpHandler : ScheduledTaskHandler() {
        override fun serviceFilters(): List<Pair<String, String>> = emptyList()
        override suspend fun handle(dTask: DTask) = HandleResult.CONTINUE
    }

    @Autowired
    private lateinit var etcdClient: EtcdClient

    private val kv get() = etcdClient.kvManager

    @Test
    fun testEtcdRewatch() {
        val futureFound = CompletableFuture<Boolean>()
        var watchID = -1L

        runBlocking {
            kv.delete("watchDir.test").awaitSingle()
            watchID = etcdClient.watchManager
                .safeStartWatch(WatchManager.WatchOptions("watchDir").prefixEnd()).awaitSingle()
            etcdClient.watchManager.subscribeEventSource(watchID) { resp ->
                log.info("pre-restart event: watchId=${resp.watchId} created=${resp.created}")
            }
        }

        etcd.restart()

        runBlocking {
            etcdClient.watchManager.deSubscribeEventSource(watchID)
            etcdClient.watchManager.subscribeEventSource(watchID) { resp ->
                log.info("post-restart event: watchId=${resp.watchId} created=${resp.created}")
                if (resp.created == true) {
                    runBlocking { kv.put("watchDir.test", "hello").awaitSingle() }
                } else {
                    resp.eventsList.forEach { evt ->
                        if (evt.type == Kv.Event.EventType.PUT &&
                            evt.kv.key.toStringUtf8() == "watchDir.test") {
                            assertEquals("hello", evt.kv.value.toStringUtf8())
                            futureFound.complete(true)
                        }
                    }
                }
            }
            assertTrue(futureFound.get(30, TimeUnit.SECONDS))
            etcdClient.watchManager.safeUnWatch(watchID)
        }
    }

    @Test
    fun testEtcdSafeWatch() {
        val result = ArrayBlockingQueue<String>(10, true)

        runBlocking {
            kv.delete("watchDir.test").awaitSingle()
            kv.put("watchDir.test", "1").awaitSingle()

            val watchRes = etcdClient.watchManager.safeWatch(
                path = "watchDir",
                prefixWatch = true,
                fullInitializeRequest = true,
                watchFromNextRevision = true,
                beforeStartWatch = { initialResponse ->
                    initialResponse.kvsList.forEach { result.add(it.value.toStringUtf8()) }
                },
                watchFlux = { resp ->
                    resp.eventsList.forEach { evt ->
                        if (evt.type == Kv.Event.EventType.PUT) result.add(evt.kv.value.toStringUtf8())
                    }
                }
            )

            assertEquals("1", result.poll(10, TimeUnit.SECONDS))
            kv.put("watchDir.test", "2").awaitSingle()
            assertEquals("2", result.poll(10, TimeUnit.SECONDS))

            etcd.restart()

            // after restart safeWatch re-initializes: should re-emit current value "2"
            assertEquals("2", result.poll(30, TimeUnit.SECONDS))

            kv.put("watchDir.test", "3").awaitSingle()
            assertEquals("3", result.poll(10, TimeUnit.SECONDS))

            etcdClient.watchManager.safeUnWatch(watchRes.watchID)
        }
    }
}
