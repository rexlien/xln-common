package xln.common.controller

import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import xln.common.dist.Cluster
import xln.common.dist.Node
import xln.common.etcd.ConfigStore
import xln.common.proto.config.ConfigOuterClass
import xln.common.proto.dist.Dist
import xln.common.utils.HttpUtils
import xln.common.utils.ProtoUtils
import xln.common.web.BaseController
import xln.common.web.BaseResponse
import xln.common.web.BaseResponseException
import xln.common.web.HttpException


@Component
@RestControllerEndpoint(id = "xln-cluster")
@ConditionalOnProperty(prefix = "xln.etcd-config", name = ["hosts"])
class ClusterController(private val cluster: Cluster, private val configStore: ConfigStore) : BaseController() {

    @GetMapping("nodes", produces=[MediaType.APPLICATION_JSON_VALUE])
    suspend fun nodes(@RequestParam("_end", defaultValue = "0") end: Int, @RequestParam("_order", defaultValue = "ASC") order: String, @RequestParam("_sort", defaultValue = "id") sort: String,
                      @RequestParam("_start", defaultValue = "0") start: Int): ResponseEntity<Map<String, String>> {

        val nodes = cluster.getNodes()
        val payload = mutableMapOf<String, String>()

        val headers = object : HttpHeaders() {
            init {
                add("Access-Control-Expose-Headers", "X-Total-Count")
                add("X-Total-Count", nodes.count().toString())
            }
        }
        nodes.forEach {
            payload[it.storeKey!!] = ProtoUtils.json(it.info)

        }
        return ResponseEntity.ok().headers(headers).body(payload)

    }


    @PostMapping("/grpc-broadcast/{serviceName}/{methodName}")
    suspend fun grpcBroadcast(
            @PathVariable("serviceName") serviceName: String, @PathVariable("methodName") methodName: String, @RequestBody payload: String) : ResponseEntity<BroadcastResponse> {
        if(cluster.isLeader()) {
            return ResponseEntity.ok(BroadcastResponse(BroadcastCode.OK, cluster.broadcast(serviceName, methodName, payload)))
        } else {
            val nodeInfo = cluster.getLeader()?.info?:return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(BroadcastResponse(BroadcastCode.LEADER_NOT_FOUND, null))

            val response = HttpUtils.httpCallMonoResponseEntity<BroadcastResponse>("http://${nodeInfo.address}:${nodeInfo.webPort}/actuator/xln-cluster/grpc-broadcast/$serviceName/$methodName", null, HttpMethod.POST
                    ,BroadcastResponse::class.java,  null, payload).awaitFirstOrNull()

            if(response == null || response.statusCode != HttpStatus.OK) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(BroadcastResponse(BroadcastCode.LEADER_NOT_FOUND, null))
            }

            return ResponseEntity.ok(response.body)

        }
    }


    @PostMapping("/configure")
    suspend fun configure(@RequestBody request: ConfigRequest) : ResponseEntity<BaseResponse> {

        val config = ProtoUtils.fromJson(request.value, ConfigOuterClass.Config::class.java, request.type)
        if(config != null) {
            configStore.store(request.directory, request.key, config)
            return ResponseEntity.ok(BaseResponse.of(ConfigureResult.OK))
        }

        throw BaseResponseException(ConfigureResult.ERROR_CONFIGURE)

    }


}