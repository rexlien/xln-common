package xln.common.test

import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import mvccpb.Kv
import org.assertj.core.util.Lists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import xln.common.proto.task.DTaskOuterClass
import xln.common.proto.task.DTaskOuterClass.DTask
import xln.common.service.EtcdClient
import xln.common.test.container.EtcdContainer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger {}

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [TestApplication::class])
@Import(EtcdPrimitivesTest.NoOpHandler::class)
@ActiveProfiles("etcd-primitives-test")
class EtcdPrimitivesTest {

    companion object {
        val network: Network = Network.newNetwork()

        val etcd = EtcdContainer(network, object : EtcdContainer.LifecycleListener {
            override fun started(c: EtcdContainer?) { log.info("EtcdPrimitivesTest: etcd started") }
            override fun failedToStart(c: EtcdContainer?, e: Exception?) { log.error("EtcdPrimitivesTest: etcd failed", e) }
            override fun stopped(c: EtcdContainer?) { log.info("EtcdPrimitivesTest: etcd stopped") }
        }, false, "prim-etcd", "prim-etcd", Lists.emptyList(), false)

        @JvmStatic
        @DynamicPropertySource
        fun dynamicProperties(registry: DynamicPropertyRegistry) {
            etcd.start()
            val port = etcd.container.firstMappedPort
            waitForPort("127.0.0.1", port)
            registry.add("xln.etcd-config.endPoint.hosts[0]") { "127.0.0.1:$port" }
        }
    }

    // Minimal handler to satisfy EtcdTaskCoordinator's List<Handler> dependency.
    @org.springframework.stereotype.Component
    class NoOpHandler : ScheduledTaskHandler() {
        override fun serviceFilters(): List<Pair<String, String>> = emptyList()
        override suspend fun handle(dTask: DTask) = HandleResult.CONTINUE
    }

    @Autowired
    private lateinit var etcdClient: EtcdClient

    private val kv get() = etcdClient.kvManager
    private val leases get() = etcdClient.leaseManager

    // ── KV primitives ────────────────────────────────────────────────────────

    @Test
    fun testPutAndGet() {
        runBlocking {
            val key = "etcd-prim-test.put-get"
            kv.put(key, "hello").awaitSingle()
            val value = kv.get(key).awaitSingle()
            assertEquals("hello", value.toStringUtf8())
            kv.delete(key).awaitSingle()
        }
    }

    @Test
    fun testDeleteRemovesKey() {
        runBlocking {
            val key = "etcd-prim-test.delete"
            kv.put(key, "bye").awaitSingle()
            kv.delete(key).awaitSingle()
            val resp = kv.getRaw(key).awaitSingle()
            assertEquals(0L, resp.count)
        }
    }

    @Test
    fun testGetReturnsRevisionAndVersion() {
        runBlocking {
            val key = "etcd-prim-test.revision"
            kv.delete(key).awaitSingle()

            kv.put(key, "v1").awaitSingle()
            val rev1 = kv.getRaw(key).awaitSingle().getKvs(0).modRevision

            kv.put(key, "v2").awaitSingle()
            val rev2 = kv.getRaw(key).awaitSingle().getKvs(0).modRevision

            assertTrue("mod revision must increase on each put", rev2 > rev1)
            kv.delete(key).awaitSingle()
        }
    }

    @Test
    fun testPrefixScan() {
        runBlocking {
            val prefix = "etcd-prim-test.prefix."
            kv.put("${prefix}a", "1").awaitSingle()
            kv.put("${prefix}b", "2").awaitSingle()
            kv.put("${prefix}c", "3").awaitSingle()

            val resp = kv.getPrefix(prefix).awaitSingle()
            assertEquals(3, resp.count)

            resp.kvsList.forEach { kv.delete(it.key.toStringUtf8()).awaitSingle() }
        }
    }

    // ── Transactions ─────────────────────────────────────────────────────────

    @Test
    fun testPutIfAbsentSucceedsWhenKeyMissing() {
        runBlocking {
            val key = "etcd-prim-test.put-if-absent.miss"
            kv.delete(key).awaitSingle()

            val opts = KVManager.PutOptions().withKey(key)
                .withValue(com.google.protobuf.ByteString.copyFromUtf8("claimed"))
            val txn = kv.transactPut(KVManager.TransactPut(opts).putIfAbsent()).awaitSingle()
            assertTrue("transaction must succeed when key is absent", txn.succeeded)

            kv.delete(key).awaitSingle()
        }
    }

    @Test
    fun testPutIfAbsentFailsWhenKeyExists() {
        runBlocking {
            val key = "etcd-prim-test.put-if-absent.exists"
            kv.put(key, "original").awaitSingle()

            val opts = KVManager.PutOptions().withKey(key)
                .withValue(com.google.protobuf.ByteString.copyFromUtf8("interloper"))
            val txn = kv.transactPut(KVManager.TransactPut(opts).putIfAbsent()).awaitSingle()
            assertTrue("transaction must fail when key already exists", !txn.succeeded)

            assertEquals("original", kv.get(key).awaitSingle().toStringUtf8())
            kv.delete(key).awaitSingle()
        }
    }

    @Test
    fun testVersionedDelete() {
        runBlocking {
            val key = "etcd-prim-test.versioned-delete"
            kv.put(key, "data").awaitSingle()
            val version = kv.getRaw(key).awaitSingle().getKvs(0).version

            val txn = kv.transactDelete(
                key, KVManager.TransactDelete().enableCompareVersion(version)
            ).awaitSingle()
            assertTrue("versioned delete must succeed with correct version", txn.succeeded)
            assertEquals(0L, kv.getRaw(key).awaitSingle().count)
        }
    }

    @Test
    fun testVersionedDeleteFailsWithStaleVersion() {
        runBlocking {
            val key = "etcd-prim-test.versioned-delete-stale"
            kv.put(key, "v1").awaitSingle()
            kv.put(key, "v2").awaitSingle()
            val currentVersion = kv.getRaw(key).awaitSingle().getKvs(0).version

            val txn = kv.transactDelete(
                key, KVManager.TransactDelete().enableCompareVersion(currentVersion - 1)
            ).awaitSingle()
            assertTrue("versioned delete must fail with stale version", !txn.succeeded)
            assertEquals(1L, kv.getRaw(key).awaitSingle().count)

            kv.delete(key).awaitSingle()
        }
    }

    // ── Proto messages ───────────────────────────────────────────────────────

    @Test
    fun testProtoMessagePutAndGet() {
        runBlocking {
            val key = "etcd-prim-test.proto"
            val src = DTaskOuterClass.DTask.newBuilder().setId("proto-test").build()
            kv.putMessage(key, src).block()
            val result = kv.getMessage(key, DTaskOuterClass.DTask::class.java).block()
            assertNotNull(result)
            assertEquals("proto-test", result!!.id)
            kv.delete(key).awaitSingle()
        }
    }

    // ── Watch ────────────────────────────────────────────────────────────────

    @Test
    fun testWatcherReceivesPutEvent() {
        runBlocking {
            val key = "etcd-prim-test.watch"
            kv.delete(key).awaitSingle()

            val watchId = etcdClient.watchManager
                .safeStartWatch(WatchManager.WatchOptions(key)).awaitSingle()
            val received = CompletableFuture<String>()
            etcdClient.watchManager.subscribeEventSource(watchId) { resp ->
                resp.eventsList.forEach { evt ->
                    if (evt.type == Kv.Event.EventType.PUT && evt.kv.key.toStringUtf8() == key) {
                        received.complete(evt.kv.value.toStringUtf8())
                    }
                }
            }

            kv.put(key, "watch-value").awaitSingle()
            assertEquals("watch-value", received.get(10, TimeUnit.SECONDS))

            etcdClient.watchManager.safeUnWatch(watchId)
            kv.delete(key).awaitSingle()
        }
    }

    // ── Lease ─────────────────────────────────────────────────────────────────

    @Test
    fun testLeaseAttachedKeyExpiresAfterTtl() {
        runBlocking {
            val key = "etcd-prim-test.lease-expiry"
            val ttlSeconds = 2L

            val lease = leases.createOrGetLease(0, ttlSeconds, false).awaitSingle()
            val leaseId = lease.response.id
            assertTrue("lease ID must be non-zero", leaseId != 0L)

            kv.put(
                KVManager.PutOptions().withKey(key)
                    .withValue(com.google.protobuf.ByteString.copyFromUtf8("ephemeral"))
                    .withLeaseID(leaseId)
            ).awaitSingle()

            assertNotNull("key must exist before lease expires",
                kv.getRaw(key).awaitSingle().takeIf { it.count > 0 })

            Thread.sleep((ttlSeconds + 1) * 1000)

            assertEquals("key must be gone after lease expiry", 0L, kv.getRaw(key).awaitSingle().count)
        }
    }
}
