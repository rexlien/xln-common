package xln.common.web.rest

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.Message
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import mu.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import xln.common.grpc.ProtobufService
import xln.common.serializer.Utils
import xln.common.service.EtcdClient
import xln.common.utils.ProtoUtils
import xln.common.web.BaseController

private val log = KotlinLogging.logger {  }

open class EtcdDocument<T: Message> {
    var id: String? = null

    //@JsonSerialize(using = JacksonProtoAnySerializer::class )
    //@JsonDeserialize(using = JacksonProtoAnyDeserializer::class)
    var content: T? = null
}

abstract class EtcdRestController<T: Message>(protected val etcd: EtcdClient, protected val protobufService: ProtobufService, private val entityClazz: Class<T>, protected val rootPrefix: String) : BaseController() {

    open suspend fun get(limit: Int, prefixKey: String ): ResponseEntity<String> {

        val total = etcd.kvManager.getPrefixCount("$rootPrefix$prefixKey").awaitSingle()
        val headers = object : HttpHeaders() {
            init {
                add("Access-Control-Expose-Headers", "X-Total-Count")
                add("X-Total-Count", total.toString())
            }
        }

        val kvs = etcd.kvManager.getPrefix("$rootPrefix$prefixKey").awaitSingle()
        val list = mutableListOf<EtcdDocument<T>>()
        kvs.kvsList.forEach {
            val msg = ProtoUtils.fromByteString(it.value, entityClazz)
            val document = createEtcdDocument(it.key.toStringUtf8(), msg)
            list.add(document)
        }

        val objectMapper = protobufService!!.createObjectMapper()

        return ResponseEntity.ok().headers(headers).body(objectMapper.writeValueAsString(list))
    }


    open suspend fun getOne(id: String): String? {
        val value = etcd.kvManager.get(id).awaitSingle().toStringUtf8()

        val document = EtcdDocument<T>()
        document.id = id;
        document.content = ProtoUtils.fromJson(value, entityClazz)

        val objectMapper = ObjectMapper()
        objectMapper.setDefaultTyping(Utils.createJsonCompliantResolverBuilder(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE))
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        try {
            return objectMapper.writeValueAsString(document)
        } catch (ex: Exception) {
            return null
        }
    }

    protected suspend fun putOne(id: String, body: String, beforeSave: suspend (oldObj:T?, obj: T) -> Unit = { _: T?, _: T -> }): ResponseEntity<Any?> {

        val objectMapper =  Utils.createObjectMapper()
        var document: EtcdDocument<T>?
        try {
            document = objectMapper.readValue(body, EtcdDocument<T>().javaClass)//readObject(body, objectMapper)
        } catch (ex: Exception) {
            log.error("jackson read failed", ex)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
        }

        if (document != null) {
            try {


                //probably no need to check collection change anymore
                val oldMessage = etcd.kvManager.get(id).awaitFirstOrNull()
                var oldObj : T? = null
                if(oldMessage != null) {
                    oldObj = ProtoUtils.fromJson(oldMessage.toStringUtf8(), entityClazz)
                }
                beforeSave(oldObj, document.content!!)
                etcd.kvManager.put(id, document.content).awaitSingle()

                //collectionChanged(oldObj, obj)

            } catch (ex: RuntimeException) {
                log.error("data modify failed", ex)
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
            }

        }
        return ResponseEntity.ok(document)


    }

    protected suspend fun deleteOne(id: String): ResponseEntity<kotlin.Any?> {

        val document = etcd.kvManager.getDocument(id, entityClazz).awaitFirstOrNull()
        if(document == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
        }
        val response = etcd.kvManager.delete(id).awaitFirstOrNull()
        if (response == null || response.deleted == 0L) {
            log.error("delete failed: $id")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
        }
        return ResponseEntity.ok().body(document)

    }

    abstract suspend fun createEtcdDocument(key: String, msg: T) : EtcdDocument<T>

}