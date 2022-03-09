package xln.common.test

import io.netty.channel.ChannelOption
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import lombok.extern.slf4j.Slf4j
import mu.KotlinLogging
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.actuate.autoconfigure.web.server.LocalManagementPort
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.HttpMethod
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import xln.common.utils.HttpUtils
import java.time.Duration

private val log = KotlinLogging.logger {}

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [TestApplication::class], webEnvironment= SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Slf4j
class WebTest2
{
    @LocalManagementPort
    private val manageMentport = 0L

    @LocalServerPort
    private val port = 0L

    @Test
    fun testTimeout() {
        runBlocking {
            val client = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(15)).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);

            val generalClient = WebClient.builder()
                    .clientConnector(ReactorClientHttpConnector(client))
                    .build()

            try {
                //connection timeout
                HttpUtils.httpRetry(generalClient.get().uri("http://127.0.0.2:${port}/timeout").retrieve().toEntity(String::class.java), 1, 0).awaitFirst()
            }catch (ex: Exception) {
                log.error("", ex)
            }

            try {
                //response timeout
                HttpUtils.httpRetry(generalClient.get().uri("http://localhost:${port}/timeout").retrieve().toEntity(String::class.java),1, 0 ).awaitFirst()
            }catch(ex: Exception) {
                log.error("", ex)
            }

            try {
                //404
               HttpUtils.httpCallMono("http://localhost:${port}/notexist", null, HttpMethod.GET, null, null).toEntity(String::class.java).awaitFirst()
            }catch (ex: Exception) {
                log.error("", ex)
            }


        }
    }
}