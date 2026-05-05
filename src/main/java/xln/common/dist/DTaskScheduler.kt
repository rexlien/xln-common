package xln.common.dist

import kotlinx.coroutines.*
import mu.KotlinLogging
import mvccpb.Kv
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import xln.common.etcd.DTaskService
import xln.common.proto.task.DTaskOuterClass.DTask
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

private val log = KotlinLogging.logger {}

@Service
@ConditionalOnBean(DTaskService::class)
@ConditionalOnProperty(prefix = "xln.dtask-config.dScheduler", name = ["enable"], havingValue = "true")
class DTaskScheduler(
    private val dTaskService: DTaskService,
    private val handlers: List<Handler>
) {

    enum class TaskType {
        TASK_REMOVED
    }
    data class TaskEvent(val key: String, val versionTask: VersionedProto<DTask>, val taskType: TaskType)
    abstract class Handler {

        //true to filter and remove task
        open suspend fun postFilterTask(dTask: DTask): Boolean {
            if (Instant.now().toEpochMilli() > dTask.scheduleConfig.end) {
                return true
            }
            return false
        }

        //return false to remove task if needed
        open suspend fun handle(dTask: DTask): Boolean {
            return true
        }

        //fire when task reaches the end of the scheduler
        open suspend fun handleEnd(dTask: DTask) {
        }

        open fun serviceFilters(): List<Pair<String, String>> {
            return mutableListOf()
        }

        //handle frequency in millis, default to 1 min
        open fun handleRate() : Long {
            return 60000L
        }
    }

    private val taskSchedulerMap = ConcurrentHashMap<String, VersionedProto<DTask>>()
    val forceTaskEventsQueue = ConcurrentLinkedDeque<TaskEvent>()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickJob: Job? = null

    @PostConstruct
    fun initialize() {
        handlers.forEach { handler ->
            handler.serviceFilters().forEach { (sg, sn) ->
                try {
                    runBlocking {
                        withContext(Dispatchers.Default) {
                            startScheduler(sg, sn)
                        }
                    }
                } catch (ex: Exception) {
                    log.warn("DTaskScheduler: failed to start scheduler for $sg.$sn, will retry on reconnect", ex)
                }
            }
        }
    }

    init {
        tickJob = scope.launch {
            var lastTick = 0L
            val handlerAccuTime = HashMap<Handler, Long>().also { map ->
                handlers.forEach { map[it] = 0L }
            }

            while (isActive) {
                delay(1000)

                val now = Instant.now().toEpochMilli()
                val deltaTime = if (lastTick == 0L) 0L else now - lastTick
                lastTick = now

                val handlersShouldFire = mutableListOf<Handler>()
                handlers.forEach { handler ->
                    val accu = (handlerAccuTime[handler] ?: 0L) + deltaTime
                    if (accu >= handler.handleRate()) {
                        handlersShouldFire.add(handler)
                        handlerAccuTime[handler] = 0L
                    } else {
                        handlerAccuTime[handler] = accu
                    }
                }

                while (!forceTaskEventsQueue.isEmpty()) {
                    val event = forceTaskEventsQueue.poll()
                    if (event.taskType == TaskType.TASK_REMOVED) {
                        if (taskSchedulerMap.containsKey(event.key)) {
                            log.debug("actively remove task: ${event.key}")
                            taskSchedulerMap.versionRemove(event.key, event.versionTask)
                            handlers.forEach { it.handleEnd(event.versionTask.value) }
                        }
                    }
                }

                taskSchedulerMap.toMap().forEach { (t, u) ->
                    val serviceInfo = dTaskService.getServiceInfoFromKey(t)
                    if (serviceInfo != null) {
                        var shouldDeleteTask = false
                        handlersShouldFire.forEach { handler ->
                            try {
                                if (!handler.handle(u.value)) {
                                    shouldDeleteTask = true
                                } else if (handler.postFilterTask(u.value)) {
                                    handler.handleEnd(u.value)
                                    shouldDeleteTask = true
                                }
                            } catch (ex: Exception) {
                                log.error("DTask handle error", ex)
                            }
                        }
                        if (shouldDeleteTask) {
                            taskSchedulerMap.versionRemove(t, u)
                            dTaskService.versionDeleteTask(serviceInfo.first, serviceInfo.second, u)
                            log.debug("task: $t safely deleted")
                        }
                    }
                }
            }
        }
    }

    suspend fun startScheduler(serviceGroup: String, service: String) {
        dTaskService.watchServiceTask(serviceGroup, service,
            watchFlux = {
                if (it.type == Kv.Event.EventType.PUT) {
                    val dTask = DTask.parseFrom(it.kv.value)
                    if (dTask.hasScheduleConfig()) {
                        taskSchedulerMap.put(it.kv.key.toStringUtf8(), VersionedProto(it.kv, dTask))
                    }
                    // one-time tasks (no ScheduleConfig) are handled by TaskCoordinatorService
                } else if (it.type == Kv.Event.EventType.DELETE) {
                    val dTask = DTask.parseFrom(it.prevKv.value)
                    forceTaskEventsQueue.add(TaskEvent(it.kv.key.toStringUtf8(), VersionedProto(it.prevKv, dTask), TaskType.TASK_REMOVED))
                }
            },
            onDisconnected = {
                taskSchedulerMap.clear()
                forceTaskEventsQueue.clear()
            })
    }

    @PreDestroy
    fun destroy() {
        taskSchedulerMap.clear()
        tickJob?.cancel()
        scope.cancel()
    }

}
