package xln.common.etcd

import com.google.protobuf.ByteString
import etcdserverpb.Rpc
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import mvccpb.Kv
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.concurrent.CustomizableThreadFactory
import org.springframework.stereotype.Service
import xln.common.Context
import xln.common.config.CommonConfig
import xln.common.config.EtcdConfig
import xln.common.proto.config.ConfigOuterClass
import xln.common.service.EtcdClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

@Service
@ConditionalOnProperty(prefix = "xln.etcd-config", name = ["hosts"])
class ConfigStore(private val etcdConfig: EtcdConfig, private val etcdClient: EtcdClient, private val context: Context) {

    data class Path(val directory: String, val key: String)


    private val PREFIX_KEY = "xln-config/"

    private val configMap = ConcurrentHashMap<String, ConcurrentHashMap<String, ConfigOuterClass.Config>>()
    private val watchManager = etcdClient.watchManager
    private val kvManager = etcdClient.kvManager

    private val customThreadFactory = CustomizableThreadFactory("xln-configStore")
    private val serializeExecutor = Executors.newFixedThreadPool(1, this.customThreadFactory)

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

        val res = watchManager.watchPath(genDir(directory), true, true)

        res.response.kvsList.forEach {

            val path = keyToPath(it.key.toStringUtf8())
            val config = ConfigOuterClass.Config.parseFrom(it.value)
            put(directory, path.key, config)
        }
        val watchID = res.watchID
        watchManager.eventSource.subscribe {

            if(watchID == it.watchId) {
                serializeExecutor.submit {

                    for (watchEvent in it.eventsList) {

                        val path = keyToPath(watchEvent.kv.key.toStringUtf8())
                        if(watchEvent.type == Kv.Event.EventType.DELETE) {
                            configMap[path.directory]?.remove(path.key)
                        } else if(watchEvent.type == Kv.Event.EventType.PUT) {

                            val config =ConfigOuterClass.Config.parseFrom(watchEvent.kv.value)
                            put(path.directory, path.key, config)
                        }

                    }

                }
            }

        }
        res.watcherTrigger.awaitSingle()

    }

    fun getConfig(directory: String, key: String) : ConfigOuterClass.Config? {
        return configMap[directory]?.get(key)
    }









}