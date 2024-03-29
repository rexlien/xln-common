package xln.common.etcd

import com.google.protobuf.ByteString
import etcdserverpb.Rpc
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import mvccpb.Kv
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.concurrent.CustomizableThreadFactory
import org.springframework.stereotype.Service
import reactor.core.Disposable
import xln.common.Context
import xln.common.config.CommonConfig
import xln.common.config.EtcdConfig
import xln.common.dist.Versioned
import xln.common.dist.VersionedProto
import xln.common.dist.versionAdd
import xln.common.grpc.GrpcFluxStream
import xln.common.proto.config.ConfigOuterClass
import xln.common.service.EtcdClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.annotation.PreDestroy

typealias WatchHandler = (watchID: Long, phase: ConfigStore.WatchPhase, events: List<Kv.Event> ) -> Unit

private val log = KotlinLogging.logger {}

@Service
@ConditionalOnProperty(prefix = "xln.etcd-config", name = ["endPoint.hosts[0]"])
class ConfigStore(private val etcdConfig: EtcdConfig, private val etcdClient: EtcdClient, private val context: Context, private val meterRegistry: MeterRegistry) {

    enum class WatchPhase {
        INIT,
        RUNNING,
    }

    data class Path(val directory: String, val key: String)
    private val PREFIX_KEY = "xln-config/"

    //private val configMap = ConcurrentHashMap<String, ConcurrentHashMap<String, Versioned>>()//ConfigOuterClass.Config>>()
    private val configMap = ConcurrentHashMap<String, ConcurrentHashMap<String, VersionedProto<ConfigOuterClass.Config>>>()
    private val meterMap = ConcurrentHashMap<String, Gauge>()

    private val watchManager = etcdClient.watchManager
    private val kvManager = etcdClient.kvManager

    private val customThreadFactory = CustomizableThreadFactory("xln-configStore")
    private val serializeExecutor = Executors.newFixedThreadPool(1, this.customThreadFactory)

    data class WatchRegistry(val handler: WatchHandler)

    private val registeredWatch = ConcurrentHashMap<String, WatchRegistry>()


    @Volatile
    private var subscribers = ConcurrentHashMap<Long, Disposable>()

    init {
        watchManager.connectionEventSource.subscribe {
            if (it == GrpcFluxStream.ConnectionEvent.RE_CONNECTED) {
                serializeExecutor.submit {
                    runBlocking {
                        restartWatch()
                    }
                }

            } else if(it == GrpcFluxStream.ConnectionEvent.DISCONNECTED) {
                serializeExecutor.submit {
                    //subscriber?.dispose()
                    cleanSubscribers()
                }
            }
        }
        startConfigWatch()
    }

    suspend fun registerWatch(directory: String, watchHandler: WatchHandler)  {

        registeredWatch[directory] = WatchRegistry(watchHandler)
        startWatch(directory, watchHandler)
        log.info("Directory:${directory} watch registered")

    }

    @PreDestroy
    private fun destroy() {
        cleanSubscribers()
        serializeExecutor.shutdown()
        log.info("Config store destroyed")
    }

    private fun cleanSubscribers() {

        subscribers.forEach {
            it.value.dispose()
        }
        subscribers.clear()
    }

    private fun startConfigWatch() {

        try {
            etcdConfig.configWatchDirs.forEach {
                runBlocking {
                    startWatch(it)
                }
            }
        }catch (ex: Exception) {
            log.error("config watch failed", ex);
        }
    }

    private suspend fun restartWatch() {

        startConfigWatch()

        try {

            registeredWatch.forEach {
                startWatch(it.key, it.value.handler)
            }

        }catch (ex: Exception) {

            log.error("registered watch failed", ex)
        }
    }



    private fun genKey(directory: String, key: String) : String {
        return "${PREFIX_KEY}${etcdConfig.configNamespace}/${directory}/${key}"
    }

    private fun genDir(directory: String) : String {
        return "${PREFIX_KEY}${etcdConfig.configNamespace}/${directory}/"
    }

    private fun keyToPath(key: String) : Path {
        val tokens = key.split("/");

        return Path(tokens[tokens.size - 2], tokens[tokens.size - 1])
    }
/*
    private fun put(directory: String, key: String, value: ConfigOuterClass.Config) {
        if (!configMap.containsKey(directory)) {
            configMap.putIfAbsent(directory, ConcurrentHashMap())
        }

        val config = VersionedProto(value)
        configMap[directory]?.versionAdd(key, config)
    }(
*/
    private fun put(directory: String, key: String, kv: Kv.KeyValue) {
        if (!configMap.containsKey(directory)) {
            configMap.putIfAbsent(directory, ConcurrentHashMap())
        }

        val config = VersionedProto(kv, ConfigOuterClass.Config.parseFrom(kv.value))
        configMap[directory]?.versionAdd(key, config)

        if(etcdConfig.isEnableVersionMeter) {
            val meterName = "xln.config.version.count"
            val path = "${directory}.${key}"
            val meterKey = "${meterName}.${path}"
            if (!meterMap.contains(meterKey)) {
                val gauge = Gauge.builder(meterName, configMap[directory], { it ->

                    val config = it?.get(key)
                    if (config != null) {
                        return@builder config.getVersion().toDouble()
                    }
                    .0
                }).tag("path", path).register(meterRegistry)
                meterMap[meterKey] = gauge
            }
        }
    }



    suspend fun store(directory: String, key: String, config: ConfigOuterClass.Config) {
        //kvManager.put(genDir(directory), config.toByteString()).awaitSingle()
        val res = kvManager.put(genKey(directory, key), config.toByteString()).awaitSingle()
    }

    private suspend fun retrieve(directory: String, key: String) : ConfigOuterClass.Config? {

        val bytes = kvManager.get(genKey(directory, key)).awaitFirstOrNull()
        if (bytes != null) {
            return ConfigOuterClass.Config.parseFrom(bytes)
        }

        return null


    }

    suspend fun startWatch(directory: String, watchHandler: WatchHandler? = null) {

        log.info("Config Directory Start Watching : ${directory}")
        //log.debug("start watch")
        val res = watchManager.watchPath(genDir(directory), true, true)


        res.response.kvsList.forEach {

            val path = keyToPath(it.key.toStringUtf8())
            //val config = ConfigOuterClass.Config.parseFrom(it.value)
            put(directory, path.key, it)

        }

        if (watchHandler != null) {
            try {

                val tmpList = mutableListOf<Kv.Event>()
                res.response.kvsList.forEach {
                    tmpList.add(Kv.Event.newBuilder().setKv(it).setType(Kv.Event.EventType.PUT).build())

                }
                watchHandler(res.watchID, WatchPhase.INIT, tmpList)
            }catch (ex: Exception) {
                log.error("Handler, error", ex)
            }
        }

        val watchID = res.watchID
        subscribers.put(watchID, watchManager.eventSource.subscribe {

            //if there's error restart watch

            if (watchID == it.watchId) {
                serializeExecutor.submit {

                    if (watchHandler != null) {
                        try {
                            watchHandler(res.watchID, WatchPhase.RUNNING, it.eventsList)
                        }catch (ex: Exception) {
                            log.error("Handler, error", ex)
                        }
                    }

                    try {
                        for (watchEvent in it.eventsList) {

                            val path = keyToPath(watchEvent.kv.key.toStringUtf8())
                            if (watchEvent.type == Kv.Event.EventType.DELETE) {
                                configMap[path.directory]?.remove(path.key)
                            } else if (watchEvent.type == Kv.Event.EventType.PUT) {

                                put(path.directory, path.key, watchEvent.kv)
                            }

                        }
                    } catch (ex: Exception) {
                        log.error("Config watch failed", ex)
                    }

                }
            }

        })
        res.watcherTrigger.awaitSingle()

    }

    fun getConfig(directory: String, key: String) : ConfigOuterClass.Config? {
        return configMap[directory]?.get(key)?.value
    }

    fun getConfigs(directory: String) : HashMap<String, VersionedProto<ConfigOuterClass.Config>> {
        return HashMap<String, VersionedProto<ConfigOuterClass.Config>>(configMap[directory])
    }

    fun getConfigWithVersion(directory: String, key: String): VersionedProto<ConfigOuterClass.Config>? {
        return configMap[directory]?.get(key)
    }









}