package xln.common.dist

import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import etcdserverpb.Rpc
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import lombok.extern.slf4j.Slf4j
import mvccpb.Kv
import org.slf4j.LoggerFactory
import xln.common.etcd.KVManager
import xln.common.etcd.KVManager.TransactOptions
import xln.common.etcd.LeaseManager
import xln.common.etcd.WatchManager
import xln.common.etcd.watchPath
import xln.common.proto.dist.Dist
import xln.common.service.EtcdClient
import xln.common.utils.FluxUtils
import java.lang.Boolean
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentSkipListMap

class Root : Versioned, ClusterAware {

    private val log = LoggerFactory.getLogger(this.javaClass);

    constructor(cluster: Cluster, selfKey: String) {

        this.cluster = cluster
        this.etcdClient = cluster.etcdClient;
        subscribeCluster(cluster)
        this.selfKey = selfKey;

    }
/*
    suspend fun join(cluster: Cluster, lease : LeaseManager.LeaseInfo) {
        val nodeKey = cluster.clusterProperty.nodeKey
        val response = cluster.etcdClient.kvManager.put(KVManager.PutOptions().withLeaseID(lease.response.id).withKey(nodeKey).withValue(cluster.clusterProperty.myNodeInfo.toByteString())).awaitSingle()
        self = Node(cluster, cluster.clusterProperty.myNodeInfo);
        self!!.setSelf(true)

    }
*/
    override fun onClusterEvent(clusterEvent: ClusterEvent) {

        when(clusterEvent) {

            is LeaderUp -> {
                log.debug("leader up")
                if(clusterEvent.leader.version > this.version()) {
                    controllerNode = clusterEvent.leader
                    if(clusterEvent.leader.info!!.key == selfKey) {
                        log.debug("become controller")
                        controllerNode?.setSelf(true)
                        runBlocking {
                            onLeaderChanged(clusterEvent, true)
                        }
                    }
                }
            }
            is LeaderDown -> {
                log.debug("leader down")
                if(clusterEvent.leader.version > this.version()) {
                    controllerNode = clusterEvent.leader
                    if(clusterEvent.leader.info!!.key == selfKey) {
                        log.debug("lost control")
                        controllerNode?.setSelf(false)
                        runBlocking {
                            onLeaderChanged(clusterEvent, false)
                        }
                    }

                }

            }
        }
    }

    override suspend fun onLeaderChanged(clusterEvent: ClusterEvent, isLeader: kotlin.Boolean) {
        if(isLeader) {
            val nodeGroup = this.cluster.getNodeGroup()
            nodeGroup.second.forEach {
                nodes[it.key] = it.value
            }
            val fluxSink = FluxUtils.createFluxSinkPair<ClusterEvent>();
            fluxSink.flux.subscribe {
                when(it) {
                    is NodeUp -> {
                        log.debug("node up")
                        if (it.node.info!!.key == selfKey) {
                            it.node.setSelf(true);
                        }
                        nodes.versionAdd(it.node.storeKey!!, it.node)//[it.node.info!!.key] = it.node
                        printNode()
                    }
                    is NodeDown -> {
                        log.debug("node down")
                        nodes.versionRemove(it.node.storeKey!!, it.node)
                        printNode()
                    }
                }
            }
            nodeWatchID = this.cluster.watchCluster(nodeGroup.first, fluxSink.sink)

        } else {

            this.cluster.unWatchCluster(nodeWatchID)
            nodeWatchID = -1L
        }
    }


    suspend fun shutdown() {
        val tmpNode = controllerNode
        tmpNode?.onShutdown()

        nodes.forEach {
            val versioned = it.value;
            when(versioned) {
                is Node -> {
                    versioned.onShutdown()
                }
            }
        }

    }

    private fun printNode() {

        nodes.forEach {
            val versioned = it.value;
            when (versioned) {
                is Node -> {
                    log.debug("key: ${it.key} value: ${versioned.info!!.name}")
                }
            }
        }
    }

    private var selfKey = "";

    @Volatile private var controllerNode : Node? = null

    private val nodes = ConcurrentSkipListMap<String, Versioned>()
    private var nodeWatchID = -1L;
    private val etcdClient : EtcdClient
    private val cluster: Cluster
    override fun version(): Long {
        return controllerNode?.version?:0L
    }

    override fun modRevision(): Long {
       return controllerNode?.modRevision?:0L
    }

    override fun createRevision(): Long {
       return controllerNode?.createRevision?:0L
    }

}


class Node : Versioned {

    var info: Dist.NodeInfo? = null

    private val log = LoggerFactory.getLogger(this.javaClass);

    private var cluster: Cluster
    private var self = false
    var storeKey: String? = null


    constructor(cluster: Cluster, info: Dist.NodeInfo) {
        this.cluster = cluster
        this.info = info
        storeKey = info.key
    }

    constructor(cluster: Cluster, info: ByteString?) {
        this.cluster = cluster
        try {
            this.info = Dist.NodeInfo.parseFrom(info)
            storeKey = this.info!!.getKey()
        } catch (ex: InvalidProtocolBufferException) {
            log.error("", ex)
        }
    }

    constructor(cluster: Cluster, keyValue: Kv.KeyValue) : this(cluster, keyValue.value) {
        modRevision = keyValue.modRevision
        version = keyValue.version
        createRevision = keyValue.createRevision
        storeKey = keyValue.key.toStringUtf8()
        try {
            this.info = Dist.NodeInfo.parseFrom(keyValue.value)
        } catch (ex: InvalidProtocolBufferException) {
                log.error("", ex)
        }

    }



    fun setSelf(self: kotlin.Boolean): Node {
        this.self = self
        return this
    }

    fun refresh(info: Dist.NodeInfo?) {
        this.info = info
    }

    fun onShutdown() {
        //log.debug("on shutdown");
        if (self && info != null) {
            log.debug("delete self:$storeKey")
            try {
                if (createRevision != 0L) {
                    log.debug("delete create revision:$createRevision")
                    val txn = cluster.etcdClient.kvManager.transactDelete(Rpc.DeleteRangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(storeKey)).build(),
                            TransactOptions().withCheckedCreateRevision(createRevision)).block(Duration.ofSeconds(3))
                    log.debug("transact delete:" + Boolean.toString(txn.succeeded))
                } else {
                    cluster.etcdClient.kvManager.delete(Rpc.DeleteRangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(storeKey)).build()).block(Duration.ofSeconds(3))
                }
            } catch (ex: RuntimeException) {
                log.warn("timeout when shutdown", ex)
            }
        }
    }

    var version = 0L
    var modRevision = 0L
    var createRevision = 0L
    override fun version(): Long {
        return version
    }

    override fun modRevision(): Long {
        return modRevision
    }

    override fun createRevision(): Long {
        return createRevision
    }
}