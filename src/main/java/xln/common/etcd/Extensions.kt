package xln.common.etcd

import com.google.protobuf.ByteString
import etcdserverpb.Rpc
import kotlinx.coroutines.reactive.awaitSingle


suspend fun WatchManager.watchPath(path: String) : Triple<Rpc.RangeResponse, Long, Long> {

    var response = client.kvManager.get(Rpc.RangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(path)).build()).awaitSingle()

    var revision: Long
    if(response.kvsCount == 0) {
        revision = response.header.revision
    } else  {
        revision = response.kvsList[0].modRevision
    }

    val watchID = nextWatchID.getAndIncrement()
    client.watchManager.startWatch(
            WatchManager.WatchOptions(path).withStartRevision(revision).withWatchID(watchID))

    return Triple(response, watchID, revision)
}