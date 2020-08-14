package xln.common.controller

import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import xln.common.dist.Cluster


@Component
@RestControllerEndpoint(id = "xln-cluster")
@ConditionalOnProperty(prefix = "xln.etcd-config", name = ["hosts"])
class ClusterController(private val cluster: Cluster) {

    @PostMapping("/grpc-broadcast/{serviceName}/{methodName}")
    suspend fun grpcBroadcast(
            @PathVariable("serviceName") serviceName: String, @PathVariable("methodName") methodName: String, @RequestBody payload: String) : BroadcastResponse {

        return BroadcastResponse(cluster.broadcast(serviceName, methodName, payload))
    }
}
