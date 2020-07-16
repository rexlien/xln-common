package xln.common.dist

import com.google.protobuf.ByteString
import etcdserverpb.Rpc
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.mono
import mvccpb.Kv
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.scheduling.concurrent.CustomizableThreadFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.retry.retryExponentialBackoff
import xln.common.Context
import xln.common.config.CommonConfig
import xln.common.etcd.KVManager.PutOptions
import xln.common.etcd.LeaseManager
import xln.common.etcd.LeaseManager.LeaseEvent
import xln.common.etcd.WatchManager
import xln.common.etcd.unwatch
import xln.common.etcd.watchPath
import xln.common.proto.dist.Dist
import xln.common.service.EtcdClient
import xln.common.utils.FluxUtils
import xln.common.utils.NetUtils
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
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

    @Volatile private var curLeaseInfo: Mono<LeaseManager.LeaseInfo> = startNewLease()
    private val serializeExecutor = Executors.newFixedThreadPool(1, this.customThreadFactory)

    @Volatile private var self : Node? = null
    @Volatile private var controllerWatchID = 0L;

    private val clusterEventSource = FluxUtils.createFluxSinkPair<ClusterEvent>()
    private @Volatile var root = Root(this, clusterProperty.nodeKey)

    private val watchSinkers  = ConcurrentHashMap<Long, FluxSink<ClusterEvent>>()



    private val leaseEvents = etcdClient.leaseManager.eventSource.publishOn(Schedulers.fromExecutor(serializeExecutor)).flatMap {
        return@flatMap mono(context = serializeExecutor.asCoroutineDispatcher()) {

            if (it.type == LeaseEvent.Type.REMOVED) {
                //removed but not crashed
                if (it.info == curLeaseInfo.awaitSingle()) {

                    log.debug("recreate lease");
                    //root = Root(this@Cluster, clusterProperty.nodeKey)
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
            if(it.canceled) {
                return@mono Unit
            }

            log.debug("watchID : $watchID received  revision : ${it.header.revision}")

            try {
                when(watchID) {
                    /*
                    nodesWatchID -> {
                        for(watchEvent in it.eventsList) {
                            if (watchEvent.type == Kv.Event.EventType.DELETE) {
                                //nodes.remove(watchEvent.kv.key.toStringUtf8())
                                clusterEventSource.second.next(NodeDown(Node(this@Cluster, watchEvent.kv)))
                            } else if (watchEvent.type == Kv.Event.EventType.PUT) {
                                clusterEventSource.second.next(NodeUp(Node(this@Cluster, watchEvent.kv)))
                                //nodes[nodeKey] = Node(this@Cluster, watchEvent.kv.value)
                            }
                        }

                     */

                    controllerWatchID -> {
                        //for(watchEvent in it.eventsList) {

                        val watchEvent = it.eventsList[0]
                            if(watchEvent.type == Kv.Event.EventType.PUT) {
                                val controllerNode = Node(this@Cluster, watchEvent.kv)
                                clusterEventSource.sink.next(LeaderUp(controllerNode!!));

                                log.info(watchEvent.kv.toString())

                            } else {
                                val controllerNode = Node(this@Cluster, watchEvent.kv)
                                clusterEventSource.sink.next(LeaderDown(controllerNode))
                                log.info(watchEvent.kv.toString())
                                var result = this@Cluster.etcdClient.kvManager.transactPut(PutOptions().withKey(this@Cluster.clusterProperty.controllerNodeDir).
                                        withValue(clusterProperty.myNodeInfo.toByteString()).withIfAbsent(true).withLeaseID(curLeaseInfo.awaitSingle().leaseID)).awaitSingle()
                                log.debug("controller put result:"+result.succeeded)
                            }
                        //}
                    }
                    else -> {
                        val sink = watchSinkers[watchID]
                        if(sink != null) {
                            for (watchEvent in it.eventsList) {
                                val node = Node(this@Cluster, watchEvent.kv)
                                if (watchEvent.type == Kv.Event.EventType.DELETE) {
                                    sink.next(NodeDown(node))
                                } else if (watchEvent.type == Kv.Event.EventType.PUT) {
                                    sink.next(NodeUp(node))
                                }
                            }
                        }
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

        self?.onShutdown()
        runBlocking {
            root.shutdown()
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
    
    private suspend fun getController() : Node? {
        var response = etcdClient.kvManager.get(Rpc.RangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(clusterProperty.controllerNodeDir)).build()).awaitSingle()
        response.header.revision
        if(response.kvsCount == 0) {
            return null
        } else {
            return Node(this, response.kvsList[0])
        }
    }


    private suspend fun createSelf(info: LeaseManager.LeaseInfo) = coroutineScope {

        log.debug("createSelf")
        self = Node(this@Cluster, clusterProperty.myNodeInfo);
        self!!.setSelf(true)

        //join this cluster
        join(self!!, info)

        //unwatch controller if there's watch in previous term
        if(controllerWatchID != -1L) {
            unWatchCluster(controllerWatchID)
            controllerWatchID = -1L
        }

        //start try create and watch controller
        val res = etcdClient.watchManager.watchPath(clusterProperty.controllerNodeDir, watchRecursively = false, watchFromNextRevision = false)
        controllerWatchID = res.watchID

        //if there's no controller before watch
        if(res.response.kvsCount == 0) {

            var result = etcdClient.kvManager.transactPut(PutOptions().withKey(clusterProperty.controllerNodeDir).withValue(clusterProperty.myNodeInfo.toByteString()).withIfAbsent(true).withLeaseID(info.response.id)).awaitSingle()
            log.debug("controller put result:"+result.succeeded)
        } else {

            mono(context = serializeExecutor.asCoroutineDispatcher())  {
                val controllerNode = Node(this@Cluster, res.response.getKvs(0))
                clusterEventSource.sink.next(LeaderUp(controllerNode!!));
            }.awaitSingle()

        }
        //actually start watch
        res.watcherTrigger.awaitSingle()



    }

    fun getClusterEventSource() : Flux<ClusterEvent> {
        return clusterEventSource.flux
    }

    suspend fun getNodeGroup() :Pair<Long, Map<String, Node>> {

        val response = etcdClient.kvManager.get(Rpc.RangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(clusterProperty.nodeDirectory)).
        setRangeEnd(ByteString.copyFromUtf8(KeyUtils.getEndKey(clusterProperty.nodeDirectory))).build()).awaitSingle()

        val ret = mutableMapOf<String, Node>();
        response.kvsList.forEach {
            val nodeInfo = Dist.NodeInfo.parseFrom(it.value);
            val node = Node(this, nodeInfo)
            ret.put(it.key.toStringUtf8(), node)
        }
        return Pair(response.header.revision, ret)

    }

    suspend fun watchCluster(revision: Long, sink: FluxSink<ClusterEvent>) : Long {

        val res = etcdClient.watchManager.watchPath(clusterProperty.nodeDirectory, true, true)
        watchSinkers[res.watchID] = sink
        res.watcherTrigger.awaitSingle()
        return res.watchID;

    }

    suspend fun unWatchCluster(watchID : Long) {

        watchSinkers.remove(watchID)
        etcdClient.watchManager.unwatch(watchID)


    }

    suspend fun join(node : Node, leaseInfo : LeaseManager.LeaseInfo) : Rpc.PutResponse {

        val nodeKey = clusterProperty.nodeKey
        return etcdClient.kvManager.put(PutOptions().withLeaseID(leaseInfo.response.id).withKey(nodeKey).withValue(node.info!!.toByteString())).awaitSingle()

    }






}