package xln.common.web.rest

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.mongodb.BasicDBObject
import com.mongodb.internal.operation.OrderBy
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import xln.common.serializer.Utils
import xln.common.service.MongoService
import xln.common.web.BaseController


/**
 * base controller class for implementing REST data provider for React-admin
 */
abstract class AdminRestController<T : Any>(protected val repository: RestMongoRepository<T>, private val entityClazz: Class<T>, protected val mongoService: MongoService) : BaseController() {

    private val log = LoggerFactory.getLogger(this.javaClass)

    protected suspend fun get(end: Int,order: String, sort: String,
                       start: Int,  filter: String): ResponseEntity<List<T>> {

        val mongoTemplate = mongoService.getSpringTemplate(ReactiveMongoTemplate::class.java)
        val filterObj = Document.parse(filter)

        val total = mongoTemplate.execute(entityClazz) {
            it.countDocuments(filterObj)
        }.awaitSingle()
        val headers = object : HttpHeaders() {
            init {
                add("Access-Control-Expose-Headers", "X-Total-Count")
                add("X-Total-Count", total.toString())
            }
        }

        val pageSize = end - start
        //val page = start / pageSize
        //val sortObj: Sort
        var sortObj : Bson
        if (order == "ASC") {
            //sortObj = Sort.by(Sort.Direction.ASC, sort)
            sortObj = BasicDBObject("_id", OrderBy.ASC.intRepresentation)
        } else {
            //sortObj = Sort.by(Sort.Direction.DESC, sort)
            sortObj = BasicDBObject("_id", OrderBy.DESC.intRepresentation)
        }
        //val pageableRequest = PageRequest.of(page, pageSize, sortObj)
        //val sortObj = """{"$sort":$sortValue}"""

        val objects: MutableList<T> = mutableListOf()
        //(repository.findAllBy(pageableRequest) as Flux<T>).asFlow().toList(objects)

        (mongoTemplate.execute(entityClazz) {

            val subscriber = Sinks.many().multicast().directBestEffort<Document>()

            val ret = subscriber.asFlux().flatMap {
                Mono.just(mongoTemplate.converter.read(entityClazz, it))
            }
            it.find(filterObj).sort(sortObj).skip(start).limit(pageSize).subscribe(object : Subscriber<Document> {
                override fun onSubscribe(s: Subscription?) {
                    s?.request(Long.MAX_VALUE)
                }

                override fun onNext(t: Document?) {
                    subscriber.tryEmitNext(t)
                }

                override fun onError(t: Throwable?) {
                    subscriber.tryEmitError(t)
                }

                override fun onComplete() {
                    subscriber.tryEmitComplete()
                }

            })
            return@execute ret

        } as Flux<T>).asFlow().toList(objects)


        return ResponseEntity.ok().headers(headers).body(objects)

    }

    protected suspend fun getMany(ids: Array<String>) :List<T> {

        val objects: MutableList<T> = mutableListOf()

        ids.forEach {
            val obj = repository.findById(ObjectId(it)).awaitSingle()
            if(obj != null) {
                objects.add(obj)
            }
        }

        return objects
    }

    protected suspend fun getOne(id: String): String? {

        val obj = repository.findById(ObjectId(id)).awaitSingle()

        val objectMapper = ObjectMapper()
        objectMapper.setDefaultTyping(Utils.createJsonCompliantResolverBuilder(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE))
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        try {
            return objectMapper.writeValueAsString(obj)
        } catch (ex: Exception) {
            return null
        }
    }

    protected suspend fun putOne(id: String, body: String, beforeSave: suspend (oldObj:T?, obj: T) -> Unit = { _: T?, _: T -> }): ResponseEntity<Any?> {

        val objectMapper =  Utils.createObjectMapper()
        var obj: T?
        try {
            obj = objectMapper.readValue(body, entityClazz)//readObject(body, objectMapper)
        } catch (ex: Exception) {
            log.error("jackson read failed", ex)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
        }

        if (obj != null) {
            try {

                //TODO: this in fact is not atomic, might have slim chance to retrieve unexpected oldObj
                var oldObj = repository.findById(ObjectId(id)).awaitFirstOrNull()
                beforeSave(oldObj, obj)
                repository.save(obj).awaitSingle()
                collectionChanged(oldObj, obj)

            } catch (ex: RuntimeException) {
                log.error("data modify failed", ex)
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
            }

        }
        return ResponseEntity.ok(obj)


    }

    protected suspend fun deleteOne(id: String): ResponseEntity<Any?> {


        var obj = repository.findById(ObjectId(id)).awaitFirstOrNull()
        if (obj != null) {
            try {
                repository.deleteById(ObjectId(id)).awaitFirstOrNull()
                collectionChanged(obj, null)

            }catch (ex: RuntimeException) {
                log.error("data delete failed", ex)
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)

            }
        }
        return ResponseEntity.ok().body(obj)

    }

    protected suspend fun createOne(body: String, afterCreated: suspend (obj: T) -> Unit = {}): ResponseEntity<Any?> {

        val objectMapper =  Utils.createObjectMapper()
        var obj: T? = null
        try {
            obj = objectMapper.readValue(body, entityClazz)

        } catch (ex: Exception) {
            log.error("jackson read failed", ex)
        }

        if (obj != null) {
            afterCreated(obj)
            try {
                repository.insert(obj).awaitSingle()
                collectionChanged(null, obj)

            } catch (ex: RuntimeException) {
                log.error("data modify failed", ex)
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
            }

        }
        return ResponseEntity.ok().body(obj)

    }

    protected open suspend fun collectionChanged(oldObj: T?, obj: T?) {

    }



}