package xln.common.etcd

import com.google.protobuf.ByteString
import etcdserverpb.Rpc
import kotlinx.coroutines.reactive.awaitSingle
import reactor.core.publisher.Mono
import xln.common.dist.KeyUtils

data class WatchResult(val response: Rpc.RangeResponse, val watchID: Long, val revision: Long, val watcherTrigger: Mono<Long>)
suspend fun WatchManager.watchPath(path: String, watchRecursively : Boolean, watchFromNextRevision: Boolean) : WatchResult {

    var response = client.kvManager.get(Rpc.RangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(path)).build()).awaitSingle()

    var revision: Long
    if(response.kvsCount == 0) {
        revision = response.header.revision
    } else  {
        revision = response.header.revision//kvsList[0].modRevision
    }
    if(watchFromNextRevision) {
        revision += 1
    }

    val watchID = nextWatchID.getAndIncrement()

    val option = WatchManager.WatchOptions(path).withStartRevision(revision ).withWatchID(watchID)
    if(watchRecursively) {
        option.withKeyEnd(KeyUtils.getEndKey(path))
    }
    //client.watchManager.startWatch(option)

    return WatchResult(response, watchID, revision, client.watchManager.startWatch(option))
}

suspend fun WatchManager.unwatch(watchID : Long) : Boolean {
    return client.watchManager.stopWatch(watchID).awaitSingle()
}

