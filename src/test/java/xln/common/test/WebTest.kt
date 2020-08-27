package xln.common.test

import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.reactive.server.HttpHandlerConnector
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.adapter.WebHttpHandlerBuilder
import reactor.core.publisher.toMono
import xln.common.utils.HttpUtils


@RunWith(SpringRunner::class)
@ActiveProfiles("test")
@WebFluxTest(controllers = [TestController::class])
class WebTest() {

    @Autowired
    private val webClient: WebTestClient? = null

    @Autowired
    private val context: ApplicationContext? = null


    @Test
    fun testHttpUtils() {
       
        runBlocking {
            val generalClient = WebClient.builder()
                    .clientConnector(HttpHandlerConnector(WebHttpHandlerBuilder.applicationContext(context).build()))
                    .build()
            try {
                HttpUtils.httpRetry(generalClient.get().uri("/internalServerError").retrieve().toEntity(String::class.java), 1, 0).awaitFirstOrNull()
            }catch (ex: Exception ) {

            }
            try {
                HttpUtils.httpRetry(generalClient.get().uri("/clientError").retrieve().toEntity(String::class.java), 1, 0).awaitFirstOrNull()

            }catch (ex :Exception) {

            }
        }
    }

}


