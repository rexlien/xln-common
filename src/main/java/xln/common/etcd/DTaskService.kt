package xln.common.etcd

import etcdserverpb.Rpc
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import xln.common.config.EtcdConfig
import xln.common.proto.task.DTaskOuterClass
import xln.common.service.EtcdClient
import xln.common.utils.ProtoUtils
import java.time.Instant
import java.util.*


@Service
@ConditionalOnProperty(prefix = "xln.etcd-config", name = ["endPoint.hosts[0]", "dTask.root"])
class DTaskService(private val etcdConfig: EtcdConfig, private val etcdClient: EtcdClient) {

    private val root = etcdConfig.getdTask().root

    private companion object {
        fun taskPath(root: String, serviceGroup: String, serviceName: String, taskId: String) : String {
            return "$root.$serviceGroup.$serviceName.tasks.$taskId"
        }
        fun uidPath(root: String, serviceGroup: String, serviceName: String) : String {
            return "$root.$serviceGroup.$serviceName.id"
        }

        fun serviceTaskPath(root: String, serviceGroup: String, serviceName: String) : String {
            return "$root.$serviceGroup.$serviceName.tasks."
        }

        fun progressPath(root: String, serviceGroup: String, serviceName: String, taskId: String) : String {
            return "$root.$serviceGroup.$serviceName.progress.$taskId"
        }

        fun serverProgressPath(root: String, serviceGroup: String, serviceName: String) : String {
            return "$root.$serviceGroup.$serviceName.progress."
        }
    }

    data class AllcateTaskParam(val input: Map<String, com.google.protobuf.Any>, val doneAction: Map<String, com.google.protobuf.Any>)
    data class AllocateTaskResult(val result: Int, val taskId: String, val taskPath: String)

    suspend fun allocateTask(serviceGroup: String, serviceName: String, option: AllcateTaskParam) : AllocateTaskResult {

        val idPath = uidPath(root, serviceGroup, serviceName)
        val taskId = etcdClient.kvManager.inc(idPath).awaitSingle().toString()

        return allocateTask(serviceGroup, serviceName, taskId, option)
    }

    suspend fun allocateTask(serviceGroup: String, serviceName: String, taskId: String, option: AllcateTaskParam) : AllocateTaskResult {

        val taskPath = taskPath(root, serviceGroup, serviceName, taskId)


        val msg = DTaskOuterClass.DTask.newBuilder().setId(taskPath).putAllInputs(option.input).putAllDoneActions(option.doneAction).
            setCreateTime(Instant.now().toEpochMilli()).build();

        val resp = etcdClient.kvManager.transactPut(KVManager.TransactPut(KVManager.PutOptions().withKey(taskPath).withMessage(msg)).putIfAbsent()).awaitSingle()
        if(!resp.succeeded) {
            return AllocateTaskResult(1, "", "")
        }

        return AllocateTaskResult(0, taskId, taskPath)
    }

    suspend fun progress(serviceGroup: String, serviceName: String, taskId: String, curProgress: Int, targetProgress: Int) : Rpc.TxnResponse {

        val progressPath = progressPath(root, serviceGroup, serviceName, taskId)

        val default = DTaskOuterClass.DTaskProgress.newBuilder().setCurProgress(curProgress).setTaskId(taskId).build()
        return etcdClient.kvManager.transactModifyAndPut(progressPath, default, DTaskOuterClass.DTaskProgress::class.java) {
            //just replace it
            default
        }.awaitSingle()

    }

    suspend fun watchTask(serviceGroup: String, serviceName: String, taskId: String, watchFlux: (response: Rpc.WatchResponse) -> Unit) : Long {

        val taskPath = taskPath(root, serviceGroup, serviceName, taskId)
        val res = etcdClient.watchManager.safeWatch(taskPath, false, false, true, {}, watchFlux)
        return res.watchID
    }

    suspend fun watchServiceTask(serviceGroup: String, serviceName: String, watchFlux: (response: Rpc.WatchResponse) -> Unit) : Long {
        val servicePath = serviceTaskPath(root, serviceGroup, serviceName)
        val res = etcdClient.watchManager.safeWatch(servicePath, true, false, true, {}, watchFlux)
        return res.watchID
    }

    suspend fun unwatchTask(watchId: Long) {
        etcdClient.watchManager.safeUnWatch(watchId)
    }

    suspend fun listTasks(serviceGroup: String, serviceName: String, limit: Long) : Map<String, DTaskOuterClass.DTask> {

        val servicePath = serviceTaskPath(root, serviceGroup, serviceName)
        val response = etcdClient.kvManager.getPrefix(servicePath, limit).awaitSingle()

        val ret = mutableMapOf<String, DTaskOuterClass.DTask>()
        response.kvsList.forEach {
            ret.put(it.key.toStringUtf8(), ProtoUtils.fromJson(it.value.toStringUtf8(), DTaskOuterClass.DTask::class.java));
        }
        return ret
    }

    suspend fun getTask(serviceGroup: String, serviceName: String, taskId: String) : DTaskOuterClass.DTask {
        val taskPath = taskPath(root, serviceGroup, serviceName, taskId)
        val value = etcdClient.kvManager.get(taskPath).awaitSingle()
        return ProtoUtils.fromJson(value.toStringUtf8(), DTaskOuterClass.DTask::class.java)

    }

    suspend fun getProgress(serviceGroup: String, serviceName: String, taskId: String) : DTaskOuterClass.DTaskProgress {
        val progressPath = progressPath(root, serviceGroup, serviceName, taskId)
        val value = etcdClient.kvManager.get(progressPath).awaitSingle()
        return ProtoUtils.fromJson(value.toStringUtf8(), DTaskOuterClass.DTaskProgress::class.java)
    }


}