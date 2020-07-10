package xln.common.utils

import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import xln.common.dist.ClusterEvent
import java.util.concurrent.CompletableFuture

data class FluxSinkPair<T>(val flux : Flux<T>, val sink: FluxSink<T>)

object FluxUtils {

/*
    fun <T> createFluxSinkPair(): Pair<Flux<T>, FluxSink<T>> {
        val sinkFuture = CompletableFuture<FluxSink<T>>()
        val flux = Flux.create<T> { sinkFuture.complete(it) }.publish().autoConnect(0)
        val sink = sinkFuture.get()
        return Pair(flux, sink);
    }
*/
    fun <T> createFluxSinkPair(): FluxSinkPair<T> {
        val sinkFuture = CompletableFuture<FluxSink<T>>()
        val flux = Flux.create<T> { sinkFuture.complete(it) }.publish().autoConnect(0)
        val sink = sinkFuture.get()
        return FluxSinkPair(flux, sink)
    }
}