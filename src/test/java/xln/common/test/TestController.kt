package xln.common.test

import kotlinx.coroutines.Delay
import kotlinx.coroutines.delay
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

private val log = KotlinLogging.logger {}

@RestController
class TestController {

    @GetMapping("/internalServerError")
    suspend fun internalServerError() : ResponseEntity<Any?> {

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
    }

    @GetMapping("/clientError")
    suspend fun clientError() : ResponseEntity<Any?> {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null)
    }


    @GetMapping("/timeout")
    suspend fun timeout() : ResponseEntity<Any?> {

        delay(Long.MAX_VALUE)
        return ResponseEntity.status(HttpStatus.OK).body(null)
    }

}