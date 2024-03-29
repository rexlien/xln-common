package xln.common.etcd

import com.google.protobuf.ByteString
import etcdserverpb.Rpc
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import reactor.core.publisher.Mono
import xln.common.dist.KeyUtils

private val log = KotlinLogging.logger {}

data class WatchResult(val response: Rpc.RangeResponse, val watchID: Long, val watcherTrigger: Mono<Long>)
suspend fun WatchManager.watchPath(path: String, watchRecursively : Boolean, watchFromNextRevision: Boolean) : WatchResult {

    var response : Rpc.RangeResponse
    if(path[path.length - 1] == '/') {
        response = client.kvManager.get(KVManager.createDirectoryRangeRequest(path)).awaitSingle()
    } else {
        response = client.kvManager.get(Rpc.RangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(path)).build()).awaitSingle()
    }
    var revision: Long
    revision = response.header.revision
    if(watchFromNextRevision) {
        revision += 1
    }

    val watchID = nextWatchID.getAndIncrement()

    val option = WatchManager.WatchOptions(path).withStartRevision(revision ).withWatchID(watchID)
    if(watchRecursively) {
        option.withKeyEnd(KeyUtils.getPrefixEnd(path))
    }
    //client.watchManager.startWatch(option)

    return WatchResult(response, watchID, client.watchManager.startWatch(option))
}

data class SafeWatchResult(val watchID: Long, val revisionToWatch: Long)

//this watch will request the initial revision first for current revision, then start to watch from the next revision.
//if full KV range is needed for complete sync, set fullInitializeRequest to true
suspend fun WatchManager.safeWatch(path: String, prefixWatch: Boolean, fullInitializeRequest: Boolean,
                                   watchFromNextRevision: Boolean,
                                   beforeStartWatch: (initialResponse: Rpc.RangeResponse) -> Unit,
                                   watchFlux: (response: Rpc.WatchResponse) -> Unit, onDisconnected : () -> Unit = {}, withPrevKv: Boolean = false): SafeWatchResult {

    val response : Rpc.RangeResponse

    if(fullInitializeRequest) {
        response = client.kvManager.get(KVManager.createRangeRequest(path, 0)).awaitSingle()
    } else {
        response = client.kvManager.get(KVManager.createRangeRequest(path, 1)).awaitSingle()
    }

    var revision: Long
    revision = response.header.revision
    if(watchFromNextRevision) {
        revision += 1
    }

    val watchID = nextWatchID.getAndIncrement()
    beforeStartWatch(response)

    client.watchManager.subscribeEventSource(watchID) {
        if (it.watchId == watchID) {
            watchFlux(it)
        }
    }


    val option = WatchManager.WatchOptions(path).withStartRevision(revision ).withWatchID(watchID).withPrevKV(withPrevKv).setDisconnectCB {
        client.watchManager.deSubscribeEventSource(it)
        try {
            onDisconnected()
        }catch (ex: Exception) {
            log.error("", ex)
        }
    }.setReconnectCB { e, v ->
        if (e == WatchManager.WatchOptions.RewatchEvent.RE_BEFORE_REWATCH) {
            client.watchManager.subscribeEventSource(watchID) {
                if (it.watchId == watchID) {
                    watchFlux(it)
                }
            }
            val response : Rpc.RangeResponse
            runBlocking {

                if(fullInitializeRequest) {
                    response = client.kvManager.get(KVManager.createRangeRequest(path, 0)).awaitSingle()
                } else {
                    response = client.kvManager.get(KVManager.createRangeRequest(path, 1)).awaitSingle()
                }

            }
            beforeStartWatch(response)
            var revision: Long
            revision = response.header.revision
            if(watchFromNextRevision) {
                revision += 1
            }
            return@setReconnectCB revision
        } else {
            return@setReconnectCB 0L
        }

    }
    if(prefixWatch) {
        option.withKeyEnd(KeyUtils.getPrefixEnd(path))
    }
    client.watchManager.safeStartWatch(option).awaitSingle()

    return SafeWatchResult(watchID, revision)


    //return WatchResult(response, watchID, revision, ))
}

suspend fun WatchManager.safeUnWatch(watchID : Long) : Boolean {

    client.watchManager.deSubscribeEventSource(watchID)
    return safeStopWatch(watchID).awaitSingle()
}

suspend fun WatchManager.unwatch(watchID : Long) : Boolean {
    return client.watchManager.stopWatch(watchID).awaitSingle()
}


