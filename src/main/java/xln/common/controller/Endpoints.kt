package xln.common.controller

import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import xln.common.dist.Cluster

@Component
@RestControllerEndpoint(id = "xln-cluster")
//@ConditionalOnProperty("xln.cluster-config.management", havingValue = "true")
//@RestController
class ClusterController(private val cluster: Cluster) {

    @PostMapping("/grpc-broadcast/{serviceName}/{methodName}")
    suspend fun grpcBroadcast(
            @PathVariable("serviceName") serviceName: String, @PathVariable("methodName") methodName: String, @RequestBody payload: String) : BroadcastResponse {

        return BroadcastResponse(cluster.broadcast(serviceName, methodName, payload))
    }
}