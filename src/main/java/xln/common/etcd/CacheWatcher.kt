package xln.common.etcd

import kotlinx.coroutines.runBlocking
import mvccpb.Kv
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import xln.common.config.CacheConfig
import xln.common.config.EtcdConfig
import xln.common.proto.command.Command
import xln.common.proto.config.ConfigOuterClass
import xln.common.service.CacheService
import xln.common.service.EtcdClient
import xln.common.utils.ProtoUtils

@Service
@ConditionalOnProperty(prefix = "xln.etcd-config", name = ["hosts"])
class CacheWatcher(private val configStore: ConfigStore, private val etcdClient: EtcdClient, private val etcdConfig: EtcdConfig, private val cacheService: CacheService) {

    private val log = LoggerFactory.getLogger(this.javaClass);

    init {
        runBlocking {
            try {
                configStore.registerWatch("xln-cache") { id, phase, events ->
                    events.forEach {
                        if (it.type == Kv.Event.EventType.PUT) {
                            val config = ConfigOuterClass.Config.parseFrom(it.kv.value)
                            val task = config.propsMap["task"]
                            if (task != null) {
                                val cacheTask = ProtoUtils.unpack(task, Command.CacheTask::class.java)
                                if (cacheTask != null) {
                                    cacheService.invalidCache(cacheTask)
                                }
                            }
                        }
                    }
                }
            }catch (ex: Exception) {
                log.error("CacheWatcher init failed")
            }
        }
    }


    suspend fun invalidate(key: String, task: Command.CacheTask) {
        configStore.store("xln-cache", key, ConfigOuterClass.Config.newBuilder().putProps("task", com.google.protobuf.Any.pack(task)).build())
    }





}