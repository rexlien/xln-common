package xln.common.test

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class TestController() {

    @GetMapping("/internalServerError")
    suspend fun internalServerError() : ResponseEntity<Any?> {

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
    }

    @GetMapping("/clientError")
    suspend fun clientError() : ResponseEntity<Any?> {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null)
    }

}