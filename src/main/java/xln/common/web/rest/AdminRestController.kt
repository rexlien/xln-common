package xln.common.web.rest

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import reactor.core.publisher.Flux
import xln.common.serializer.Utils
import xln.common.web.BaseController

abstract class AdminRestController<T : Any>(protected val repository: RestMongoRepository<T>) : BaseController() {

    private val log = LoggerFactory.getLogger(this.javaClass);

    open suspend fun get(@RequestParam("_end") end: Int, @RequestParam("_order") order: String, @RequestParam("_sort") sort: String,
                       @RequestParam("_start") start: Int): ResponseEntity<List<T>> {

        val total = repository.count().awaitSingle()
        val headers = object : HttpHeaders() {
            init {
                add("Access-Control-Expose-Headers", "X-Total-Count")
                add("X-Total-Count", total.toString())
            }
        }

        val pageSize = end - start
        val page = start / pageSize
        val sortObj: Sort
        if (order == "ASC") {
            sortObj = Sort.by(Sort.Direction.ASC, sort)
        } else {
            sortObj = Sort.by(Sort.Direction.DESC, sort)
        }
        val pageableRequest = PageRequest.of(page, pageSize, sortObj)

        var objects: MutableList<T> = mutableListOf()

        (repository.findAllBy(pageableRequest) as Flux<T>).asFlow().toList(objects)
        return ResponseEntity.ok().headers(headers).body(objects)


    }

    open suspend fun getOne(@PathVariable("id") id: String): String? {

        val badge = repository.findById(ObjectId(id)).awaitSingle()

        val objectMapper = ObjectMapper()
        objectMapper.setDefaultTyping(Utils.createJsonCompliantResolverBuilder(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE))
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        try {
            return objectMapper.writeValueAsString(badge)
        } catch (ex: Exception) {
            return null
        }
    }

    open suspend fun putOne(@PathVariable("id") id: String, @RequestBody body: String): ResponseEntity<Any?> {

        val objectMapper =  Utils.createObjectMapper()
        var obj: T?
        try {
            obj = readObject(body, objectMapper)
        } catch (ex: Exception) {
            log.error("jackson read failed", ex)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
        }

        if (obj != null) {
            try {

                beforeObjectChanged(obj)
                repository.save(obj).awaitSingle()
                afterObjectChanged(obj)
                collectionChanged(obj)

            } catch (ex: RuntimeException) {
                log.error("data modify failed", ex)
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
            }

        }
        return ResponseEntity.ok(obj)


    }

    open suspend fun deleteOne(@PathVariable("id") id: String): ResponseEntity<Any?> {


        var obj = repository.findById(ObjectId(id)).awaitFirstOrNull()
        if (obj != null) {
            try {
                beforeObjectDelete(obj)
                repository.deleteById(ObjectId(id)).awaitSingle()
                afterObjectDelete(obj)
                collectionChanged(obj)

            }catch (ex: RuntimeException) {
                log.error("delete failed", ex)
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)

            }
        }
        return ResponseEntity.ok().body(obj)

    }

    open suspend fun createOne(@RequestBody body: String): ResponseEntity<Any?> {

        val objectMapper =  Utils.createObjectMapper()
        var obj: T? = null
        try {
            obj = readObject(body, objectMapper)
        } catch (ex: Exception) {
            log.error("jackson read failed", ex)
        }

        if (obj != null) {
            try {
                beforeObjectCreated(obj)
                repository.insert(obj).awaitSingle()
                afterObjectCreated(obj)
                collectionChanged(obj)

            } catch (ex: RuntimeException) {
                log.error("badge modify failed", ex)
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
            }

        }
        return ResponseEntity.ok().body(obj)

    }


    protected abstract fun readObject(body: String, objectMapper: ObjectMapper) : T?


    protected open suspend fun beforeObjectChanged(obj: T) {

    }

    protected open suspend fun beforeObjectCreated(obj: T) {

    }

    protected open suspend fun beforeObjectDelete(obj: T) {

    }

    protected open suspend fun afterObjectChanged(obj: T) {

    }

    protected open suspend fun afterObjectCreated(obj: T) {

    }

    protected open suspend fun afterObjectDelete(obj: T) {

    }

    protected open suspend fun collectionChanged(obj: T) {

    }



}