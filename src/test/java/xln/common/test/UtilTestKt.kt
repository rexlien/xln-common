package xln.common.test

import com.google.protobuf.Any
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import mvccpb.Kv
import org.assertj.core.util.Lists
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
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
import xln.common.dist.DTaskScheduler
import xln.common.etcd.*
import xln.common.proto.command.Command.TestKafkaPayLoad
import xln.common.proto.config.ConfigOuterClass
import xln.common.proto.task.DTaskOuterClass
import xln.common.proto.task.DTaskOuterClass.DTask
import xln.common.proto.task.DTaskOuterClass.DTaskProgress
import xln.common.service.EtcdClient
import xln.common.test.container.EtcdContainer
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [TestApplication::class])
@Import(UtilTestKt.TestHandler::class)
@ActiveProfiles("test")
class UtilTestKt {

    @Autowired
    private val etcdClient: EtcdClient? = null

    @Autowired
    private val configStore: ConfigStore? = null

    @Autowired
    private val dTaskService: DTaskService? = null

    @Autowired
    private val applicationContext: ApplicationContext? = null

    companion object {

        val logger = LoggerFactory.getLogger(UtilTestKt::class.java)

        val etcd = EtcdContainer(Network.SHARED, object : EtcdContainer.LifecycleListener {
            override fun started(container: EtcdContainer?) {

                logger.info("started")
            }

            override fun failedToStart(container: EtcdContainer?, exception: Exception?) {

                logger.info("failed start")
            }

            override fun stopped(container: EtcdContainer?) {

                logger.info("stopped")
            }

        }, false, "test", "127.0.0.1", Lists.emptyList(), true)


        @JvmStatic
        @DynamicPropertySource
        fun dynamicProperties(registry: DynamicPropertyRegistry) {

            etcd.start()

            val port = etcd.container.firstMappedPort
            registry.add("xln.etcd-config.endPoint.hosts") { mutableListOf("127.0.0.1:$port") }


        }
    }

    @Test
    fun testConfigStore() {
        runBlocking {


        }
    }

    @Test
    fun testConfigStoreWatch() {

        runBlocking {

            //configStore?.store("test", "key", ConfigOuterClass.Config.newBuilder().putProps("propKey", Any.pack(
            //        TestKafkaPayLoad.newBuilder().setPayload("hello").build())).build())

            configStore?.startWatch("test")


            configStore?.store(
                "test", "key", ConfigOuterClass.Config.newBuilder().putProps(
                    "propKey", Any.pack(
                        TestKafkaPayLoad.newBuilder().setPayload("hello2").build()
                    )
                ).build()
            )
            Thread.sleep(5000)


        }
    }

    @Test
    fun testEtcdProtoMessage() {
        runBlocking {

            val srcDtask = DTaskOuterClass.DTask.newBuilder().setId("test").build()
            etcdClient?.kvManager?.putMessage("test.Message", srcDtask)?.block()
            val dtask = etcdClient?.kvManager?.getMessage("test.Message", DTaskOuterClass.DTask::class.java)?.block()

            assert(srcDtask.id == dtask!!.id)

        }
    }

    @Test
    fun testEtcdWatcher() {

        runBlocking {

            etcdClient?.let {

                it.kvManager.delete("watchDir.test").awaitSingle()
                val watchID =
                    it.watchManager.safeStartWatch(WatchManager.WatchOptions("watchDir").prefixEnd()).awaitSingle()
                val futureFound = CompletableFuture<Boolean>()
                it.watchManager.subscribeEventSource(watchID) {
                    logger.info("got event: ${it.watchId}")
                    var found = false
                    it.eventsList.forEach {

                        if (it.type == Kv.Event.EventType.PUT) {
                            if (it.kv.key.toStringUtf8() == "watchDir.test") {
                                assert(it.kv.value.toStringUtf8() == "hello")
                                found = true
                            }
                        }
                    }
                    if (found) {
                        futureFound.complete(found)
                    }
                }

                it.kvManager.put("watchDir.test", "hello").awaitSingle()
                assert(futureFound.get() == true)


            }

        }

    }

    //check rewatch works if start watch before etcd restart
    @Test
    fun testEtcdRewatch() {

        assert(etcdClient != null)
        val futureFound = CompletableFuture<Boolean>()
        var watchID = -1L
        runBlocking {
            etcdClient?.let {
                it.kvManager.delete("watchDir.test").awaitSingle()

                watchID =
                    it.watchManager.safeStartWatch(WatchManager.WatchOptions("watchDir").prefixEnd()).awaitSingle()
                logger.info("watchID: $watchID")

                it.watchManager.subscribeEventSource(watchID) {
                    logger.info("got event: ${it.watchId}: created: ${it.created} : ${it.eventsList}")

                }
            }
        }

        etcd.restart()

        runBlocking {
            etcdClient?.let {

                it.watchManager.deSubscribeEventSource(watchID)
                //confirme watch will recreated, and send put to make sure watch still works
                it.watchManager.subscribeEventSource(watchID) { it2 ->

                    logger.info("got event: ${it2.watchId}: created: ${it2.created} : ${it2.eventsList}")
                    if (it2.created == true) {
                        runBlocking {
                            it.kvManager.put("watchDir.test", "hello").awaitSingle()
                        }
                    } else {
                        var found = false
                        it2.eventsList.forEach { it3 ->

                            if (it3.type == Kv.Event.EventType.PUT) {
                                if (it3.kv.key.toStringUtf8() == "watchDir.test") {
                                    assert(it3.kv.value.toStringUtf8() == "hello")
                                    found = true
                                }
                            }
                        }
                        if (found) {
                            futureFound.complete(found)
                        }
                    }
                }

                assert(futureFound.get() == true)
                it.watchManager.safeUnWatch(watchID)
            }

        }

    }

    @Test
    fun testEtcdSafeWatch() {
        assert(etcdClient != null)
        var value = ""
        val result = ArrayBlockingQueue<String>(10, true);
        runBlocking {
            etcdClient?.let {
                it.kvManager.delete("watchDir.test").awaitSingle()
                it.kvManager.put("watchDir.test", "1").awaitSingle()
                val watchRes = it.watchManager.safeWatch(
                    path = "watchDir",
                    prefixWatch = true,
                    fullInitializeRequest = true,
                    watchFromNextRevision = true,
                    beforeStartWatch = {
                        it.kvsList.forEach {

                            value = it.value.toStringUtf8()
                            logger.info("initialize value: $value")
                            result.add(value)
                        }

                    },
                    watchFlux = {
                        logger.info("got event: ${it.watchId} revision: ${it.header.revision} created: ${it.created} : ${it.eventsList}")
                        it.eventsList.forEach {
                            if (it.type == Kv.Event.EventType.PUT) {
                                value = it.kv.value.toStringUtf8()
                                result.add(value)
                            }
                        }
                    })


                assert(result.take() == "1")
                it.kvManager.put("watchDir.test", "2").awaitSingle()
                assert(result.take() == "2")

                //assume etcd down is down and value become stale
                value = "0"
                etcd.restart()

                //check correctly re-initalize after reconnected
                assert(result.take() == "2")

                //check watcher still works
                val resp = it.kvManager.put("watchDir.test", "3").awaitSingle()
                logger.info("put revision: $resp.header")

                assert(result.take() == "3")

                it.watchManager.safeUnWatch(watchRes.watchID)

            }
        }
    }

    @Test
    fun testDtaskService() {

        runBlocking {
            assert(dTaskService != null)
            dTaskService?.let {
                val res = dTaskService.allocateTask(
                    "my-service-group",
                    "my-service",
                    DTaskService.AllocateTaskParam(mutableMapOf(), mutableMapOf())
                )
                assert(res.result == 0)

                val taskMap = dTaskService.listTasks("my-service-group", "my-service", 0)
                assert(taskMap.contains(res.taskPath))

                taskMap.forEach { t, u ->
                    Assert.assertFalse(u.value.hasScheduleConfig())

                }

                var progressRes = dTaskService.progress("my-service-group", "my-service", res.taskId, 0, 100)
                assert(progressRes.succeeded)

                var progress = dTaskService.getProgress("my-service-group", "my-service", res.taskId);
                assert(progress!!.curProgress == 0)

                progressRes = dTaskService.progress("my-service-group", "my-service", res.taskId, 1, 100)
                assert(progressRes.succeeded)

                progress = dTaskService.getProgress("my-service-group", "my-service", res.taskId);
                assert(progress!!.curProgress == 1)

            }
        }
    }


    @Test
    fun testScheduleDTaskCreateAndCancel() {

        runBlocking {

            assert(dTaskService != null)
            dTaskService?.let {
                val res = dTaskService.scheduleTask(
                    "my-service-group",
                    "my-schedule-service",
                    "testId",
                    DTaskService.ScheduleTaskParam(0, 0)
                )

                dTaskService.progress("my-service-group", "my-schedule-service", "testId", 0, 100)
                dTaskService.setProgressState(
                    "my-service-group",
                    "my-schedule-service",
                    res.task!!,
                    "testKey",
                    DTaskOuterClass.DTask.getDefaultInstance()
                )

                dTaskService.cancelTask("my-service-group", "my-schedule-service", res.taskId)
                val task = dTaskService.getTask("my-service-group", "my-schedule-service", res.taskId)
                assert(task == null)
                val progressState = dTaskService.getProgressState("my-service-group", "my-schedule-service", res.taskId)
                assert(progressState == null)
                val progress = dTaskService.getProgress("my-service-group", "my-schedule-service", "testId")
                assert(progress == null)


            }
        }
    }


    @Component
    class TestHandler : DTaskScheduler.Handler() {
        val forceFinishfuture = CompletableFuture<Boolean>()
        val endTestFuture = CompletableFuture<Boolean>()

        override suspend fun handle(dTask: DTask) : Boolean{
            logger.info("Haneld: ${dTask.id}")
            return true
        }

        override suspend fun handleEnd(dTask: DTask) {

            logger.info("HandleEnd: ${dTask.id}")
            if(dTask.id == "finishedTask") {
                forceFinishfuture.complete(true)
            } else {
                endTestFuture.complete(true)
            }
        }

        override fun serviceFilters(): List<Pair<String, String>> {
            return mutableListOf(Pair("watch-service-group", "my-schedule-service"))
        }

        override fun handleRate(): Long {
            return 3000L
        }

    }

    @Test
    fun testDTaskHandler() {
        runBlocking {
            val handler = applicationContext?.getBean(TestHandler::class.java)
            dTaskService?.let {
                val res = dTaskService.scheduleTask(
                    "watch-service-group",
                    "my-schedule-service",
                    "testId",
                    DTaskService.ScheduleTaskParam(Instant.now().toEpochMilli(), Instant.now().toEpochMilli()+ 10000)
                )

                //send infinity task for actively cancelling task later
                val task2 = dTaskService.scheduleTask(
                    "watch-service-group",
                    "my-schedule-service",
                    "finishedTask",
                    DTaskService.ScheduleTaskParam(Instant.now().toEpochMilli(), Instant.now().toEpochMilli()+ 999999999)
                )
            }

            handler!!.endTestFuture!!.get()
            val succeeded = dTaskService!!.cancelTask("watch-service-group", "my-schedule-service", "finishedTask")
            assert(succeeded)
            handler!!.forceFinishfuture!!.get()
            //wait to confirm task deleted when schedule end
            Thread.sleep(2000)
            val task = dTaskService?.getTask("watch-service-group", "my-schedule-service", "testId")
            assert(task == null)


        }
    }
}