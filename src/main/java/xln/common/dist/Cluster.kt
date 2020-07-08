package xln.common.dist

import com.google.common.collect.ImmutableMap
import com.google.protobuf.ByteString
import etcdserverpb.Rpc
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.mono
import mvccpb.Kv
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.scheduling.concurrent.CustomizableThreadFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.retry.retryExponentialBackoff
import xln.common.Context
import xln.common.config.CommonConfig
import xln.common.etcd.KVManager.PutOptions
import xln.common.etcd.LeaseManager
import xln.common.etcd.LeaseManager.LeaseEvent
import xln.common.etcd.WatchManager
import xln.common.etcd.watchPath
import xln.common.proto.dist.Dist
import xln.common.service.EtcdClient
import xln.common.utils.NetUtils
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicLong
import javax.annotation.PreDestroy

@Component
@ConditionalOnBean(EtcdClient::class)
class ClusterProperty(private val commonConfig: CommonConfig, private val context : Context ) {


    val nodeKey = KeyUtils.getNodeKey(commonConfig.appName, context.phase)
    val controllerNodeDir = KeyUtils.getControllerNode(commonConfig.appName, context.phase)
    val nodeDirectory = KeyUtils.getNodeDirectory(commonConfig.appName, context.phase)
    val myNodeInfo = Dist.NodeInfo.newBuilder().setKey(nodeKey).setName(NetUtils.getHostName()).setAddress(NetUtils.getHostAddress()).build()
}


@Service
@ConditionalOnBean(EtcdClient::class)
class Cluster(val clusterProperty: ClusterProperty, val etcdClient: EtcdClient) {

    private val random = Random()
    private val customThreadFactory = CustomizableThreadFactory("xln-cluster-")

    private val log = LoggerFactory.getLogger(this.javaClass);
    private val nodes: ConcurrentHashMap<String, Node> = ConcurrentHashMap()
    @Volatile private var selfNode: Node? = null
    @Volatile private var controllerNode : Node? = null
    @Volatile private var curLeaseInfo: Mono<LeaseManager.LeaseInfo> = startNewLease()
    private val serializeExecutor = Executors.newFixedThreadPool(1, this.customThreadFactory)

    private val nextWatchID = AtomicLong(1)
    @Volatile private var nodesWatchID = 0L
    @Volatile private var controllerWatchID = 0L

    private val leaseEvents = etcdClient.leaseManager.eventSource.publishOn(Schedulers.fromExecutor(serializeExecutor)).flatMap {
        return@flatMap mono(context = serializeExecutor.asCoroutineDispatcher()) {

            if (it.type == LeaseEvent.Type.REMOVED) {
                //removed but not crashed
                if (it.info == curLeaseInfo.awaitSingle()) {
                    //curLeaseInfo.awaitSingle()
                    log.debug("recreate lease");
                    curLeaseInfo = startNewLease()
                }
                //}
            } //else if (it.type == LeaseEvent.Type.ADDED) {

            //}

        }
    }.publish().connect()

    private val watchEvents = etcdClient.watchManager.eventSource.flatMap {

        return@flatMap mono(context = serializeExecutor.asCoroutineDispatcher()) {
            var watchID = it.watchId
            //var watchEvent = it.eventsList
            if(it.created) {
                return@mono Unit
            }

            log.debug("watchEvent : $watchID received  revision : ${it.header.revision}")

            try {
                when(watchID) {
                    nodesWatchID -> {
                        for(watchEvent in it.eventsList) {
                            if (watchEvent.type == Kv.Event.EventType.DELETE) {
                                nodes.remove(watchEvent.kv.key.toStringUtf8())
                            } else if (watchEvent.type == Kv.Event.EventType.PUT) {
                                val nodeKey = watchEvent.kv.key.toStringUtf8();
                                nodes[nodeKey] = Node(this@Cluster, watchEvent.kv.value)
                            }
                        }
                    }
                    controllerWatchID -> {
                        //for(watchEvent in it.eventsList) {

                        val watchEvent = it.eventsList[0]
                            if(watchEvent.type == Kv.Event.EventType.PUT) {
                                controllerNode = Node(this@Cluster, watchEvent.kv)
                                if(controllerNode!!.info!!.key!!.equals(this@Cluster.clusterProperty.nodeKey)) {
                                    log.info("I am controller")
                                    controllerNode!!.setSelf()
                                    this@Cluster.refreshNodes()
                                }
                                log.info(watchEvent.kv.toString())

                            } else {
                                controllerNode = null;
                                var result = this@Cluster.etcdClient.kvManager.transactPut(PutOptions().withKey(this@Cluster.clusterProperty.controllerNodeDir).
                                        withValue(clusterProperty.myNodeInfo.toByteString()).withIfAbsent(true).withLeaseID(curLeaseInfo.awaitSingle().leaseID)).awaitSingle()
                                log.debug("controller put result:"+result.succeeded)
                            }
                        //}
                    }
                }


            }catch (ex : Exception) {
                log.error("", ex)
            }
            Unit
        }

    }.publish().connect()




    @PreDestroy
    private fun destroy() {

        this.etcdClient.watchManager.stopWatch(controllerWatchID)
        controllerWatchID = -1

        selfNode?.onShutdown()
        controllerNode?.onShutdown()
        for ((_, value) in nodes) {
            value.onShutdown()
        }
        leaseEvents.dispose()
        watchEvents.dispose()

    }

    private fun startNewLease() : Mono<LeaseManager.LeaseInfo> {
        val ret = createSelfLease().cache()
        ret.subscribe { _ -> log.debug("cluster inited")}
        return ret;
    }

    private fun createSelfLease(): Mono<LeaseManager.LeaseInfo> {
        return etcdClient.leaseManager.createOrGetLease(0, 20, true, 5000).retryExponentialBackoff( Long.MAX_VALUE,
                Duration.ofSeconds(10), Duration.ofSeconds(20), true).flatMap {
            return@flatMap mono(context = serializeExecutor.asCoroutineDispatcher()) {
                try {
                    createSelf(it)
                }catch (ex : Exception) {

                    //TODO maybe should just casuse whole lease failed, and get lease again.
                    log.error("create self failed", ex)
                }
                it
            }
        }
    }

    private suspend fun refreshNodes() : Long{
        
        var respose = etcdClient.kvManager.get(Rpc.RangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(clusterProperty.nodeDirectory)).
                setRangeEnd(ByteString.copyFromUtf8(KeyUtils.getEndKey(clusterProperty.nodeDirectory))).build()).awaitSingle()

        nodes.clear()
        respose.kvsList.forEach {
            val nodeInfo = Dist.NodeInfo.parseFrom(it.value);
            val node = Node(this, nodeInfo)
            nodes.put(it.key.toStringUtf8(), node)
        }

        //log.info(respose.toString())
        return respose.header.revision;
        
    }

    private suspend fun getController() : Node? {
        var response = etcdClient.kvManager.get(Rpc.RangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(clusterProperty.controllerNodeDir)).build()).awaitSingle()
        response.header.revision
        if(response.kvsCount == 0) {
            return null
        } else {
            return Node(this, response.kvsList[0])
        }
    }

    fun getNodes() : ImmutableMap<String, Node> {
        return ImmutableMap.copyOf(nodes)
    }


    private suspend fun createSelf(info: LeaseManager.LeaseInfo) = coroutineScope {

        log.debug("createSelf")
        val nodeKey = clusterProperty.nodeKey
        val response = etcdClient.kvManager.put(PutOptions().withLeaseID(info.response.id).withKey(nodeKey).withValue(clusterProperty.myNodeInfo.toByteString())).awaitSingle()

        val node = Node(this@Cluster, clusterProperty.myNodeInfo);
        node.setSelf()
        selfNode = node

        val res = etcdClient.watchManager.watchPath(clusterProperty.controllerNodeDir)
        controllerWatchID = res.second
        if(res.first.kvsCount == 0) {

            var result = etcdClient.kvManager.transactPut(PutOptions().withKey(clusterProperty.controllerNodeDir).withValue(clusterProperty.myNodeInfo.toByteString()).withIfAbsent(true).withLeaseID(info.response.id)).awaitSingle()
            log.debug("controller put result:"+result.succeeded)
        }

    }

    private suspend fun syncNodes() {

        val revision = refreshNodes()
        nodesWatchID = nextWatchID.getAndIncrement()
        this.etcdClient.watchManager.startWatch(
                WatchManager.WatchOptions(clusterProperty.nodeDirectory).withKeyEnd(KeyUtils.getEndKey(clusterProperty.nodeDirectory)).withStartRevision(revision + 1).withWatchID(nodesWatchID))
    }



}