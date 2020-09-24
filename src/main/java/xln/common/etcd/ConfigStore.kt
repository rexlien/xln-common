package xln.common.etcd

import com.google.protobuf.ByteString
import etcdserverpb.Rpc
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import mvccpb.Kv
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.concurrent.CustomizableThreadFactory
import org.springframework.stereotype.Service
import reactor.core.Disposable
import xln.common.Context
import xln.common.config.CommonConfig
import xln.common.config.EtcdConfig
import xln.common.grpc.GrpcFluxStream
import xln.common.proto.config.ConfigOuterClass
import xln.common.service.EtcdClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.annotation.PreDestroy

@Service
@ConditionalOnProperty(prefix = "xln.etcd-config", name = ["hosts"])
class ConfigStore(private val etcdConfig: EtcdConfig, private val etcdClient: EtcdClient, private val context: Context) {


    private val log = LoggerFactory.getLogger(this.javaClass);

    data class Path(val directory: String, val key: String)

    private val PREFIX_KEY = "xln-config/"

    private val configMap = ConcurrentHashMap<String, ConcurrentHashMap<String, ConfigOuterClass.Config>>()
    private val watchManager = etcdClient.watchManager
    private val kvManager = etcdClient.kvManager

    private val customThreadFactory = CustomizableThreadFactory("xln-configStore")
    private val serializeExecutor = Executors.newFixedThreadPool(1, this.customThreadFactory)


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
        restartWatch()
    }

    @PreDestroy
    private fun destroy() {
        cleanSubscribers()
    }

    private fun cleanSubscribers() {

        subscribers.forEach {
            it.value.dispose()
        }
        subscribers.clear()
    }

    private fun restartWatch() {

        try {
            etcdConfig.configWatchDirs.forEach {
                runBlocking {
                    log.info("config Watch Directoring : ${it}")
                    startWatch(it)
                }
            }
        }catch (ex: Exception) {
            log.error("config watch failed", ex);
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

    private fun put(directory: String, key: String, value: ConfigOuterClass.Config) {
        if (!configMap.containsKey(directory)) {
            configMap.putIfAbsent(directory, ConcurrentHashMap())
        }
        configMap[directory]?.put(key, value)
    }


    suspend fun store(directory: String, key: String, config: ConfigOuterClass.Config) {
        //kvManager.put(genDir(directory), config.toByteString()).awaitSingle()
        val res = kvManager.put(genKey(directory, key), config.toByteString()).awaitSingle()
    }

    suspend fun retrieve(directory: String, key: String) : ConfigOuterClass.Config? {

        val bytes = kvManager.get(genKey(directory, key)).awaitFirstOrNull()
        if (bytes != null) {
            return ConfigOuterClass.Config.parseFrom(bytes)
        }

        return null


    }

    suspend fun startWatch(directory: String) {

        log.debug("start watch")
        val res = watchManager.watchPath(genDir(directory), true, true)


        res.response.kvsList.forEach {

            val path = keyToPath(it.key.toStringUtf8())
            val config = ConfigOuterClass.Config.parseFrom(it.value)
            put(directory, path.key, config)
        }
        val watchID = res.watchID
        subscribers.put(watchID, watchManager.eventSource.subscribe {

            //if there's error restart watch

            if (watchID == it.watchId) {
                serializeExecutor.submit {
                    try {
                        for (watchEvent in it.eventsList) {

                            val path = keyToPath(watchEvent.kv.key.toStringUtf8())
                            if (watchEvent.type == Kv.Event.EventType.DELETE) {
                                configMap[path.directory]?.remove(path.key)
                            } else if (watchEvent.type == Kv.Event.EventType.PUT) {

                                val config = ConfigOuterClass.Config.parseFrom(watchEvent.kv.value)
                                put(path.directory, path.key, config)
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
        return configMap[directory]?.get(key)
    }









}