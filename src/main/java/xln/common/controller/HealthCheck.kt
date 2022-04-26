package xln.common.controller

import io.grpc.ConnectivityState
import kotlinx.coroutines.runBlocking
import org.springframework.boot.actuate.health.AbstractReactiveHealthIndicator
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import xln.common.service.EtcdClient
import java.util.concurrent.Executors

private val scheduler = Schedulers.newSingle("xln-health-check")

@Component
@ConditionalOnProperty(prefix = "xln.etcd-config.endPoint", name = ["hosts[0]"])
class EtcdHealthIndicator(private val etcdClient: EtcdClient) : AbstractReactiveHealthIndicator() {


    override fun doHealthCheck(builder: Health.Builder?): Mono<Health> {

        return Mono.create<Health> {

                val conState = etcdClient.getConnectivityState(false)
                if (conState == ConnectivityState.IDLE || conState == ConnectivityState.READY) {
                    it.success(builder!!.up().build())

                } else {
                    it.success(builder!!.down().build())
                }


        }.publishOn(scheduler)


    }

}