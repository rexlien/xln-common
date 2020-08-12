package xln.common.test

import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.runBlocking
import lombok.extern.slf4j.Slf4j
import org.junit.Test
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import xln.common.config.ClusterConfig
import xln.common.grpc.GrpcReflection

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [TestApplication::class])
@ActiveProfiles("test")
@Slf4j
class GrpcTest {

    private val logger = LoggerFactory.getLogger(GrpcTest::class.java)
    @Autowired
    private val clusterConfig: ClusterConfig? = null

    @Test
    fun testReflection() {
        val channel = ManagedChannelBuilder.forAddress("localhost", 47000).usePlaintext().build()
        val grpcReflection = GrpcReflection()

        runBlocking {
            grpcReflection.createReflection(channel)
            val json = """{

                }"""
            val response = grpcReflection.callMethodJson(channel, "RocksLogService", "GetLogCount", json)
            if(response != null) {
                logger.info(response)
            }


        }
    }
}