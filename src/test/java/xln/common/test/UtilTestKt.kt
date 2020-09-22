package xln.common.test

import com.google.protobuf.Any
import kotlinx.coroutines.runBlocking
import lombok.extern.slf4j.Slf4j
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import xln.common.etcd.ConfigStore
import xln.common.proto.command.Command.TestKafkaPayLoad
import xln.common.proto.config.ConfigOuterClass

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [TestApplication::class])
@ActiveProfiles("test")
class UtilTestKt {


    @Autowired
    private val configStore: ConfigStore? = null

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

}