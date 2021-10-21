package xln.common.test

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.Any
import etcdserverpb.Rpc.PutResponse
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import xln.common.etcd.ConfigStore
import xln.common.etcd.DTaskService
import xln.common.proto.command.Command.TestKafkaPayLoad
import xln.common.proto.config.ConfigOuterClass
import xln.common.proto.task.DTaskOuterClass
import xln.common.serializer.Utils
import xln.common.service.EtcdClient

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [TestApplication::class])
@ActiveProfiles("test")
class UtilTestKt {

    @Autowired
    private val etcdClient: EtcdClient? = null
    @Autowired
    private val configStore: ConfigStore? = null

    @Autowired
    private val dTaskService: DTaskService? = null

    @Test
    fun testConfigStore() {
        runBlocking {



        }
    }

    @Test
    fun testConfigStoreWatch(){

        runBlocking {

            //configStore?.store("test", "key", ConfigOuterClass.Config.newBuilder().putProps("propKey", Any.pack(
            //        TestKafkaPayLoad.newBuilder().setPayload("hello").build())).build())

            configStore?.startWatch("test")


            configStore?.store("test", "key", ConfigOuterClass.Config.newBuilder().putProps("propKey", Any.pack(
                    TestKafkaPayLoad.newBuilder().setPayload("hello2").build())).build())
            Thread.sleep(5000)



        }
    }

    @Test fun testEtcdDocument() {
        runBlocking {

            val srcDtask = DTaskOuterClass.DTask.newBuilder().setId("test").build()
            etcdClient?.kvManager?.put("xln-task.my-project.my-service", srcDtask)?.block()
            val dtask = etcdClient?.kvManager?.getDocument("xln-task.my-project.my-service", DTaskOuterClass.DTask::class.java)?.block()

            assert(srcDtask.id == dtask!!.id)

        }
    }

    @Test fun testDtaskService() {

        runBlocking {
            assert(dTaskService != null)
            dTaskService?.let {
                val res = dTaskService.allocateTask("my-service-group", "my-service", DTaskService.AllcateTaskParam(mutableMapOf(), mutableMapOf()))
                assert(res.result == 0)

                val taskMap = dTaskService.listTasks("my-service-group", "my-service", 0)
                assert(taskMap.contains(res.taskPath))

                var progressRes = dTaskService.progress("my-service-group", "my-service", res.taskId, 0, 100)
                assert(progressRes.succeeded)

                var progress = dTaskService.getProgress("my-service-group", "my-service", res.taskId);
                assert(progress.curProgress == 0)

                progressRes = dTaskService.progress("my-service-group", "my-service", res.taskId, 1, 100)
                assert(progressRes.succeeded)

                progress = dTaskService.getProgress("my-service-group", "my-service", res.taskId);
                assert(progress.curProgress == 1)

            }
        }
    }




}