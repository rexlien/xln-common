package xln.common.etcd

import com.google.protobuf.Message
import etcdserverpb.Rpc
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import mvccpb.Kv
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import xln.common.dist.VersioneWrapper
import xln.common.proto.task.DTaskOuterClass
import xln.common.service.EtcdClient
import java.time.Instant

private val log = KotlinLogging.logger {}

@Service
@ConditionalOnProperty(prefix = "xln", name = ["etcd-config.endPoint.hosts[0]", "dtask-config.root"])
class DTaskService(private val dTaskConfig: DTaskConfig, private val etcdClient: EtcdClient) {

    private val root = dTaskConfig.root

    private val taskBufferIdMap =  runBlocking {

        val ret = mutableMapOf<String, String?>()
        dTaskConfig.dScheduler.configs.forEach {

            ret.put("${it.serviceGroup}.${it.serviceName}", it.messageTopic)
        }
        ret
    }

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

        fun statePath(root: String, serviceGroup: String, serviceName: String, taskId: String) : String {
            return "$root.$serviceGroup.$serviceName.state.$taskId"
        }

        //fun serverProgressPath(root: String, serviceGroup: String, serviceName: String) : String {
          //  return "$root.$serviceGroup.$serviceName.progress."
        //}


    }

    data class AllocateTaskParam(val input: Map<String, com.google.protobuf.Any> = mutableMapOf(), val doneAction: Map<String, com.google.protobuf.Any> = mutableMapOf())
    data class AllocateTaskResult(val result: Int, val taskId: String, val taskPath: String, val task: VersioneWrapper<DTaskOuterClass.DTask>? = null)
    data class ScheduleTaskParam(val start: Long, val end: Long, val params: AllocateTaskParam = AllocateTaskParam())

    suspend fun allocateTask(serviceGroup: String, serviceName: String, option: AllocateTaskParam) : AllocateTaskResult {

        val idPath = uidPath(root, serviceGroup, serviceName)
        val taskId = etcdClient.kvManager.inc(idPath).awaitSingle().toString()

        return allocateTask(serviceGroup, serviceName, taskId, option)
    }

    suspend fun allocateTask(serviceGroup: String, serviceName: String, taskId: String, option: AllocateTaskParam) : AllocateTaskResult {

        val taskPath = taskPath(root, serviceGroup, serviceName, taskId)
        val msg = DTaskOuterClass.DTask.newBuilder().setId(taskId).putAllInputs(option.input).putAllDoneActions(option.doneAction).
            setCreateTime(Instant.now().toEpochMilli()).build();

        val resp = etcdClient.kvManager.transactPut(KVManager.TransactPut(KVManager.PutOptions().withKey(taskPath).withMessage(msg)).putIfAbsent()).awaitSingle()

        if(!resp.succeeded) {
            return AllocateTaskResult(1, "", "")
        }

        return AllocateTaskResult(0, taskId, taskPath)
    }

    suspend fun scheduleTask(serviceGroup: String, serviceName: String, taskId: String, option: ScheduleTaskParam) : AllocateTaskResult {

        val taskPath = taskPath(root, serviceGroup, serviceName, taskId)

        val scheduleConfig = DTaskOuterClass.ScheduleConfig.newBuilder().setStart(option.start).setEnd(option.end)
        val msg = DTaskOuterClass.DTask.newBuilder().setId(taskId).putAllInputs(option.params.input).putAllDoneActions(option.params.doneAction).
            setCreateTime(Instant.now().toEpochMilli()).setScheduleConfig(scheduleConfig).build();

        val putOption = KVManager.PutOptions().withPrevKV(true).withKey(taskPath).withValue(msg.toByteString())
        val resp = etcdClient.kvManager.put(putOption).awaitFirstOrNull()//transactPut(KVManager.TransactPut(KVManager.PutOptions().withKey(taskPath).withMessage(msg))).awaitSingle()
        if(resp == null) {
            return AllocateTaskResult(1, "", "")
        }

        //log.info("PrevKV: ${resp.prevKv.version} : ${resp.prevKv.createRevision} : ${resp.prevKv.modRevision}")

        return AllocateTaskResult(0, taskId, taskPath, VersioneWrapper(msg, resp))
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

    suspend fun watchServiceTask(
        serviceGroup: String,
        serviceName: String,
        watchFlux: (response: Kv.Event) -> Unit,
        onDisconnected: () -> Unit
    ): Long {
        val servicePath = serviceTaskPath(root, serviceGroup, serviceName)
        val res = etcdClient.watchManager.safeWatch(servicePath, true, true, true,
            beforeStartWatch = {
                it.kvsList.forEach {
                    watchFlux(Kv.Event.newBuilder().setType(Kv.Event.EventType.PUT).setKv(it).build())
                }
            },
            watchFlux = {
                it.eventsList.forEach {
                    watchFlux(it)
                }
            }, onDisconnected, withPrevKv = true)
        return res.watchID
    }

    suspend fun unwatchTask(watchId: Long) {
        etcdClient.watchManager.safeUnWatch(watchId)
    }

    suspend fun listTasks(serviceGroup: String, serviceName: String, limit: Long = 0) : Map<String, VersioneWrapper<DTaskOuterClass.DTask>> {

        val servicePath = serviceTaskPath(root, serviceGroup, serviceName)
        val response = etcdClient.kvManager.getPrefix(servicePath, limit).awaitSingle()

        val ret = mutableMapOf<String, VersioneWrapper<DTaskOuterClass.DTask>>()
        response.kvsList.forEach {
            ret.put(it.key.toStringUtf8(), VersioneWrapper(DTaskOuterClass.DTask.parseFrom(it.value), it));
        }
        return ret
    }

    suspend fun getTask(serviceGroup: String, serviceName: String, taskId: String) : VersioneWrapper<DTaskOuterClass.DTask>? {
        val taskPath = taskPath(root, serviceGroup, serviceName, taskId)
        return getTask(taskPath)
    }

    suspend fun getTask(taskKey: String) : VersioneWrapper<DTaskOuterClass.DTask>? {

        val value = etcdClient.kvManager.getRaw(taskKey).awaitSingle()

        if(value.kvsCount == 0 ) {
            return null
        } else {
            val kv = value.getKvs(0)
            return VersioneWrapper(DTaskOuterClass.DTask.parseFrom(kv.value), kv)
        }
    }


    //delete task only when it match the give create rev and version.
    suspend fun safeDeleteTask(serviceGroup: String, serviceName: String, curTask: VersioneWrapper<DTaskOuterClass.DTask> ) : Boolean {

        val taskPath = taskPath(root, serviceGroup, serviceName, curTask.value.id)
        val resp = etcdClient.kvManager.transactDelete(taskPath, KVManager.TransactDelete().
            enableCompareCreateRevision(curTask.getCreateRevision()).enableCompareVersion(curTask.getVersion())).awaitSingle()

        return resp.succeeded

    }

    suspend fun cancelTask(serviceGroup: String, serviceName: String, taskId: String ) : Boolean {

        val taskPath = taskPath(root, serviceGroup, serviceName, taskId)
        val progressPath = progressPath(root, serviceGroup, serviceName, taskId)
        val statePath = statePath(root, serviceGroup, serviceName, taskId)
        log.debug("deleting task resources with key: $taskPath, Progress: $progressPath , State $statePath", )
        val resp = etcdClient.kvManager.transactDelete(taskPath, KVManager.TransactDelete(), listOf(progressPath, statePath)).awaitSingle()

        return resp.succeeded

    }

    suspend fun getProgress(serviceGroup: String, serviceName: String, taskId: String) : DTaskOuterClass.DTaskProgress? {
        val progressPath = progressPath(root, serviceGroup, serviceName, taskId)
        val value = etcdClient.kvManager.getRaw(progressPath).awaitSingle()
        if(value.kvsCount == 0) {
            return null
        } else {
            return DTaskOuterClass.DTaskProgress.parseFrom(value.getKvs(0).value)
        }
    }

    fun createProgressStateBuilder(task: VersioneWrapper<DTaskOuterClass.DTask>, states : Map<String,Message>) : DTaskOuterClass.DTaskProgressState.Builder {
        val taskVersion = DTaskOuterClass.TaskVersion.newBuilder().setRevision(task.getModRevision()).setTaskVersion(task.getVersion()).
            setTaskCreateVersion(task.getCreateRevision()).build()

        val anyStates = states.mapValues {
            com.google.protobuf.Any.pack(it.value)
        }
        val progressState = DTaskOuterClass.DTaskProgressState.newBuilder().
            setTaskVersion(taskVersion).setTaskId(task.value.id).putAllStates(anyStates)

        return progressState
    }

    //set given task's progress state
    suspend fun setProgressState(serviceGroup: String, serviceName: String, task: VersioneWrapper<DTaskOuterClass.DTask>, key: String, state: Message) {

        val statePath = statePath(root, serviceGroup, serviceName, task.value.id)
        val builder = createProgressStateBuilder(task, mutableMapOf(key to state))
        etcdClient.kvManager.put(statePath, builder.build().toByteString()).awaitSingle()
    }

    suspend fun setProgressState(serviceGroup: String, serviceName: String, progressState: DTaskOuterClass.DTaskProgressState) {

        val statePath = statePath(root, serviceGroup, serviceName, progressState.taskId)
        etcdClient.kvManager.put(statePath, progressState.toByteString()).awaitSingle()
    }

    suspend fun getProgressState(serviceGroup: String, serviceName: String, taskId : String) : VersioneWrapper<DTaskOuterClass.DTaskProgressState>? {
        val statePath = statePath(root, serviceGroup, serviceName, taskId)
        val resp =  etcdClient.kvManager.getRaw(statePath).awaitSingle()

        if(resp.count == 0L) {
            return null
        }
        val kv = resp.getKvs(0)
        return VersioneWrapper(DTaskOuterClass.DTaskProgressState.parseFrom(kv.value), kv)

    }

    suspend fun deleteProgressState(serviceGroup: String, serviceName: String, taskId: String) : Boolean {

        val statePath = statePath(root, serviceGroup, serviceName, taskId)
        val resp = etcdClient.kvManager.delete(statePath).awaitSingle()
        if(resp.deleted > 0) {
            return true
        }
        return false
    }

    fun getTaskBufferId(serviceGroup: String, serviceName: String) : String {
        val bufferId = taskBufferIdMap["$serviceGroup.$serviceName"]
        return bufferId ?: "$serviceGroup.$serviceName"
    }

    fun getServiceInfoFromKey(key: String) : Pair<String, String>? {
        val tokens = key.split(".")
        if(tokens.size >= 3) {
            return Pair(tokens[1], tokens[2])
        }
        return null
    }

}