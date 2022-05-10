package xln.common.etcd

import etcdserverpb.Rpc
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import xln.common.config.EtcdConfig
import xln.common.dist.VersioneWrapper
import xln.common.proto.task.DTaskOuterClass
import xln.common.service.EtcdClient
import java.time.Instant


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

    data class AllcateTaskParam(val input: Map<String, com.google.protobuf.Any> = mutableMapOf(), val doneAction: Map<String, com.google.protobuf.Any> = mutableMapOf())
    data class AllocateTaskResult(val result: Int, val taskId: String, val taskPath: String)
    //data class GetTaskResult(val result: DTaskOuterClass.DTask, val versionInfo : Pair<Long, Long>)
    data class ScheduleTaskParam(val start: Long, val end: Long, val params: AllcateTaskParam)

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

    suspend fun scheduleTask(serviceGroup: String, serviceName: String, taskId: String, option: ScheduleTaskParam) : AllocateTaskResult? {

        val taskPath = taskPath(root, serviceGroup, serviceName, taskId)

        val scheduleConfig = DTaskOuterClass.ScheduleConfig.newBuilder().setStart(option.start).setEnd(option.end)
        val msg = DTaskOuterClass.DTask.newBuilder().setId(taskPath).putAllInputs(option.params.input).putAllDoneActions(option.params.doneAction).
            setCreateTime(Instant.now().toEpochMilli()).setScheduleConfig(scheduleConfig).build();

        val resp = etcdClient.kvManager.put(taskPath, msg.toByteString()).awaitFirstOrNull()//transactPut(KVManager.TransactPut(KVManager.PutOptions().withKey(taskPath).withMessage(msg))).awaitSingle()
        if(resp == null) {
            return null
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
            ret.put(it.key.toStringUtf8(), DTaskOuterClass.DTask.parseFrom(it.value));
        }
        return ret
    }

    suspend fun getTask(serviceGroup: String, serviceName: String, taskId: String) : VersioneWrapper<DTaskOuterClass.DTask>? {
        val taskPath = taskPath(root, serviceGroup, serviceName, taskId)
        val value = etcdClient.kvManager.getRaw(taskPath).awaitSingle()

        if(value.kvsCount == 0 ) {
            return null
        } else {
            val kv = value.getKvs(0)
            return VersioneWrapper(DTaskOuterClass.DTask.parseFrom(kv.value), kv)
        }

    }


    //delete task only when it match the give create rev and version.
    suspend fun safeDeleteTask(serviceGroup: String, serviceName: String, taskId: String, curVersion: VersioneWrapper<DTaskOuterClass.DTask> ) : Boolean {

        val taskPath = taskPath(root, serviceGroup, serviceName, taskId)
        val resp = etcdClient.kvManager.transactDelete(taskPath, KVManager.TransactDelete().
            enableCompareCreateRevision(curVersion.getCreateRevision()).enableCompareVersion(curVersion.getVersion())).awaitSingle()

        return resp.succeeded

    }

    suspend fun getProgress(serviceGroup: String, serviceName: String, taskId: String) : DTaskOuterClass.DTaskProgress {
        val progressPath = progressPath(root, serviceGroup, serviceName, taskId)
        val value = etcdClient.kvManager.get(progressPath).awaitSingle()
        return DTaskOuterClass.DTaskProgress.parseFrom(value)
    }


}