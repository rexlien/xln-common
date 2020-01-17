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
    @Volatile private var leaseInfo: LeaseManager.LeaseInfo? = null
    private val scheduler: ScheduledExecutorService = etcdClient.scheduler
    private val serializeExecutor = Executors.newFixedThreadPool(1, this.customThreadFactory)

    private val nextWatchID = AtomicLong(1)
    @Volatile private var nodesWatchID = 0L
    @Volatile private var controllerWatchID = 0L
    @Volatile private var curLeaseID = 0L


    private val leaseEvents = etcdClient.leaseManager.eventSource.publishOn(Schedulers.fromExecutor(serializeExecutor)).flatMap {
        return@flatMap mono(context = serializeExecutor.asCoroutineDispatcher()) {

            if (it.type == LeaseEvent.Type.REMOVED) {
                //removed but not crashed
                if (it.info === leaseInfo) {
                    val leaseInfo = createSelfLease().awaitSingle()
                    curLeaseID = leaseInfo.response.id
                    createSelf(leaseInfo)
                }
            } else if (it.type == LeaseEvent.Type.ADDED) {

            }

        }
    }.publish().connect()

    private val watchEvents = etcdClient.watchManager.eventSource.flatMap {

        return@flatMap mono(context = serializeExecutor.asCoroutineDispatcher()) {
            var watchID = it.watchId
            //var watchEvent = it.eventsList
            if(it.created) {
                return@mono Unit
            }

            //log.info("$watchEvent received")

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
                                var result = this@Cluster.etcdClient.kvManager.transactPut(PutOptions().withKey(this@Cluster.clusterProperty.controllerNodeDir).withIfAbsent(true).withLeaseID(curLeaseID)).awaitSingle()
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

    private fun startup() {

        createSelfLease().flatMap {
            return@flatMap mono(context = serializeExecutor.asCoroutineDispatcher()) {
                val leaseInfo = it;
                curLeaseID = it.response.id
                createSelf(leaseInfo)
            }
        }.subscribe {

            log.info("init cluster complete");
        }
    }

    private fun createSelfLease(): Mono<LeaseManager.LeaseInfo> {
        return etcdClient.leaseManager.createOrGetLease(0, 20, true, 5000).retryExponentialBackoff(10,
                Duration.ofSeconds(5),  Duration.ofSeconds(600), true)
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



    private suspend fun startClaimController(info: LeaseManager.LeaseInfo) {

        var response = etcdClient.kvManager.get(Rpc.RangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(clusterProperty.controllerNodeDir)).build()).awaitSingle()

        var revision = 0L
        if(response.kvsCount == 0) {
            revision = response.header.revision
        } else  {
            revision = response.kvsList[0].modRevision
        }
        controllerWatchID = nextWatchID.getAndIncrement()
        this.etcdClient.watchManager.startWatch(
                WatchManager.WatchOptions(clusterProperty.controllerNodeDir).withStartRevision(revision).withWatchID(controllerWatchID))

        //no controller node
        if(response.kvsCount == 0) {

            var result = this.etcdClient.kvManager.transactPut(PutOptions().withKey(clusterProperty.controllerNodeDir).withValue(clusterProperty.myNodeInfo.toByteString()).withIfAbsent(true).withLeaseID(info.response.id)).awaitSingle()
            log.debug("controller put result:"+result.succeeded)
        }
    }

    /*
    public List<Node> getSiblingNodes() {
        //etcdClient.getKvManager().get()
    }
*/
    private suspend fun createSelf(info: LeaseManager.LeaseInfo) = coroutineScope {

        val nodeKey = clusterProperty.nodeKey
        //val nodeInfo = Dist.NodeInfo.newBuilder().setKey(nodeKey).setName(NetUtils.getHostName()).setAddress(NetUtils.getHostAddress()).build()
        val response = etcdClient.kvManager.put(PutOptions().withLeaseID(info.response.id).withKey(nodeKey).withValue(clusterProperty.myNodeInfo.toByteString())).awaitSingle()

        var node = async(context = serializeExecutor.asCoroutineDispatcher()) {
            leaseInfo = info
            val node = Node(this@Cluster, clusterProperty.myNodeInfo);
            node.setSelf()
            selfNode = node
            return@async selfNode
        }.await()

        startClaimController(info)

        //onSelfCreated(node)

    }

    protected suspend fun syncNodes() {

        val revision = refreshNodes()
        nodesWatchID = nextWatchID.getAndIncrement()
        this.etcdClient.watchManager.startWatch(
                WatchManager.WatchOptions(clusterProperty.nodeDirectory).withKeyEnd(KeyUtils.getEndKey(clusterProperty.nodeDirectory)).withStartRevision(revision + 1).withWatchID(nodesWatchID))
    }


    init {
        startup()
    }
}