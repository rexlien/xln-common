package xln.common.dist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import mvccpb.Kv
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Service
import xln.common.etcd.DTaskService
import xln.common.proto.task.DTaskOuterClass.DTask
import xln.common.service.SchedulerService
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.PreDestroy

private val log = KotlinLogging.logger {}

@Service
//@ConditionalOnBean(DTaskService::class)
class DTaskScheduler(
    private val dTaskService: DTaskService,
    private val schedulerService: SchedulerService,
    private val handlers: List<Handler>
) {

    //data class TaskWrapper(val dTask: DTask, var done: Boolean)
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
    }

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
        }
    }


    private val taskSchedulerMap = ConcurrentHashMap<String, VersionedProto<DTask>>()

    //since quartz is difficult to reason about and manipulate, for simplicity, iterate and check dtask schedule every second
    private val jobKey = schedulerService.schedule("xln-dTask-scheduler", Instant.now().toEpochMilli(), -1, 1000) {
        runBlocking {
            withContext(Dispatchers.Default) {
                taskSchedulerMap.toMap().forEach { (t, u) ->
                    val serviceInfo = dTaskService.getServiceInfoFromKey(t)
                    if (serviceInfo != null) {
                        log.debug("Handle for service: ${serviceInfo.first} - ${serviceInfo.second}")
                        handlers.forEach {
                            try {
                                if(!it.handle(u.value)) {
                                    //should wrap cancelling logic as much as possible
                                    taskSchedulerMap.versionRemove(t, u)
                                    dTaskService.safeDeleteTask(serviceInfo.first, serviceInfo.second, u)
                                    log.debug("task: $t safely deleted")
                                }  else if(it.postFilterTask(u.value)) {
                                    //
                                    it.handleEnd(u.value)

                                    taskSchedulerMap.versionRemove(t, u)
                                    dTaskService.safeDeleteTask(serviceInfo.first, serviceInfo.second, u)
                                    log.debug("task: $t safely deleted")
                                }

                            } catch (ex: Exception) {
                                log.error("DTask handle error", ex)
                            }
                        }
                    }
                }
            }
        }
        //never quit quartz
        false
    }


    suspend fun startScheduler(serviceGroup: String, service: String) {

        dTaskService.watchServiceTask(serviceGroup, service,
            watchFlux = {
                if (it.type == Kv.Event.EventType.PUT) {

                    val dTask = DTask.parseFrom(it.kv.value)
                    if (dTask.hasScheduleConfig()) {
                        taskSchedulerMap.put(it.kv.key.toStringUtf8(), VersionedProto(it.kv, dTask))  //TaskWrapper(dTask, false)
                    } else {
                        taskSchedulerMap.remove(it.kv.key.toStringUtf8())
                    }
                } else if (it.type == Kv.Event.EventType.DELETE) {
                    taskSchedulerMap.remove(it.kv.key.toStringUtf8())
                }
            },
            onDisconnected = {
                taskSchedulerMap.clear()

            })
    }


    @PreDestroy
    fun destroy() {
        taskSchedulerMap.clear()
        schedulerService.deleteJob(jobKey)
    }

}
