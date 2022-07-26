package xln.common.dist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import mvccpb.Kv
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import xln.common.etcd.DTaskService
import xln.common.proto.task.DTaskOuterClass.DTask
import xln.common.service.SchedulerService
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import javax.annotation.PreDestroy

private val log = KotlinLogging.logger {}

@Service
@ConditionalOnBean(SchedulerService::class)
@ConditionalOnProperty(prefix = "xln.dtask-config.dScheduler", name = ["enable"], havingValue = "true")
class DTaskScheduler(
    private val dTaskService: DTaskService,
    private val schedulerService: SchedulerService,
    private val handlers: List<Handler>
) {

    //data class TaskWrapper(val dTask: DTask, var done: Boolean)
    enum class TaskType {
        TASK_REMOVED
    }
    data class TaskEvent(val key: String, val versionTask: VersionedProto<DTask>, val taskType: TaskType)
    abstract class Handler {

        //true to filter and remove task task
        open suspend fun postFilterTask(dTask: DTask): Boolean {
            //filter the task if
            if (Instant.now().toEpochMilli() > dTask.scheduleConfig.end) {
                return true
            }
            return false
        }

        //return false to remove task if needed
        open suspend fun handle(dTask: DTask): Boolean {
            return true
        }

        //fire when task reach the end of scheduler
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

    //record last tick, DO NOT use in coroutine
    private var lastTick = ThreadLocal.withInitial {0L}
    private val handlerAccuTime = HashMap<Handler, ThreadLocal<Long>>()

    init {
        handlers.forEach {
            val services = it.serviceFilters()
            services.forEach {
                runBlocking {
                    withContext(Dispatchers.Default) {
                        startScheduler(it.first, it.second)
                    }
                }
            }
            handlerAccuTime[it] = ThreadLocal.withInitial{0L}
        }
    }

    private val taskSchedulerMap = ConcurrentHashMap<String, VersionedProto<DTask>>()
    private val forceTaskEventsQueue = ConcurrentLinkedDeque<TaskEvent>()



    //since quartz is difficult to reason about and manipulate, for simplicity, iterate and check dtask schedule every second
    private val jobKey = schedulerService.schedule("xln-dTask-scheduler", Instant.now().toEpochMilli(), -1, 1000) {

        var deltaTime = 0L
        //delta time always 0 when first time,
        if(lastTick.get() == 0L) {
            lastTick.set(Instant.now().toEpochMilli())
        } else {
            deltaTime = Instant.now().toEpochMilli() - lastTick.get()
            lastTick.set(Instant.now().toEpochMilli())
        }

        //log.debug("$deltaTime")

        //will only fire handle according to handle rate
        val handlersShouldFire = mutableListOf<Handler>()
        handlers.forEach {
            val accuTime = handlerAccuTime[it]
            if(accuTime != null) {
                val curAccu = accuTime.get() + deltaTime
                if(curAccu >= it.handleRate()) {
                    //log.debug("fire handler")
                    handlersShouldFire.add(it)
                    //reset accu
                    accuTime.set(0L)
                } else {
                    accuTime.set(curAccu)
                }
            }
        }

        runBlocking {
            withContext(Dispatchers.Default) {

                //iterate the forced remove task event to remove the task and call handleEnd for last time
                while(!forceTaskEventsQueue.isEmpty()) {
                    val event = forceTaskEventsQueue.poll()
                    if(event.taskType == TaskType.TASK_REMOVED) {

                        //this should only happen when removed by event triggered externally
                        if(taskSchedulerMap.containsKey(event.key)) {
                            log.debug("actively remove task: ${event.key}")
                            taskSchedulerMap.versionRemove(event.key, event.versionTask)
                            handlers.forEach {
                                it.handleEnd(event.versionTask.value)
                            }
                        }
                    }
                }
                taskSchedulerMap.toMap().forEach { (t, u) ->
                    val serviceInfo = dTaskService.getServiceInfoFromKey(t)
                    if (serviceInfo != null) {
                        //log.debug("Handle for service: ${serviceInfo.first} - ${serviceInfo.second}")
                        var shouldDeleteTask = false

                        //TODO: maybe we still want to handleEnd in near real-time interval
                        handlersShouldFire.forEach {
                            try {
                                if(!it.handle(u.value)) {
                                    shouldDeleteTask = true
                                }  else if(it.postFilterTask(u.value)) {
                                    it.handleEnd(u.value)
                                    shouldDeleteTask = true
                                }
                            } catch (ex: Exception) {
                                log.error("DTask handle error", ex)
                            }
                        }
                        if(shouldDeleteTask) {
                            taskSchedulerMap.versionRemove(t, u)
                            dTaskService.versionDeleteTask(serviceInfo.first, serviceInfo.second, u)
                            log.debug("task: $t safely deleted")
                        }
                    }
                }
            }
        }
        //never quit quartz
        false
    }


    suspend fun startScheduler(serviceGroup: String, service: String) {

        //NOTE: when remove by etcd event, will put the task to a pending queue for executing lastEnd
        dTaskService.watchServiceTask(serviceGroup, service,
            watchFlux = {
                if (it.type == Kv.Event.EventType.PUT) {

                    val dTask = DTask.parseFrom(it.kv.value)
                    if (dTask.hasScheduleConfig()) {
                        taskSchedulerMap.put(it.kv.key.toStringUtf8(), VersionedProto(it.kv, dTask))  //TaskWrapper(dTask, false)
                    } else {
                        forceTaskEventsQueue.add(TaskEvent(it.kv.key.toStringUtf8(),VersionedProto(it.kv, dTask), TaskType.TASK_REMOVED))
                    }
                } else if (it.type == Kv.Event.EventType.DELETE) {
                    val dTask = DTask.parseFrom(it.prevKv.value)
                    //taskSchedulerMap.remove(it.kv.key.toStringUtf8())
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
        schedulerService.deleteJob(jobKey)
    }

}
