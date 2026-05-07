package xln.common.dist

import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitSingle
import mu.KotlinLogging
import mvccpb.Kv
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Service
import xln.common.etcd.DTaskConfig
import xln.common.etcd.DTaskService
import xln.common.proto.task.DTaskOuterClass.DTask
import xln.common.proto.task.DTaskOuterClass.TaskStatus
import xln.common.service.EtcdClient
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

private val log = KotlinLogging.logger {}

@Service
@ConditionalOnBean(DTaskService::class)
class EtcdTaskCoordinator(
    private val dTaskService: DTaskService,
    private val dTaskConfig: DTaskConfig,
    private val etcdClient: EtcdClient,
    private val oneTimeHandlers: List<OneTimeTaskHandler> = emptyList(),
    private val scheduledHandlers: List<ScheduledTaskHandler> = emptyList()
) {
    data class ScheduledTaskEntry(
        val task: VersioneWrapper<DTask>,
        val serviceGroup: String,
        val serviceName: String
    )

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // serviceKey -> leaseId (one lease per service, shared across all tasks)
    private val leaseMap = ConcurrentHashMap<String, Long>()

    // taskId -> (serviceGroup, serviceName) for one-time tasks in flight
    private val heldClaims = ConcurrentHashMap<String, Pair<String, String>>()

    // taskId -> entry for scheduled tasks currently claimed by this pod
    private val scheduledTaskMap = ConcurrentHashMap<String, ScheduledTaskEntry>()

    // taskId -> currently running tick coroutine; prevents concurrent ticks for the same task
    private val activeTickJobs = ConcurrentHashMap<String, Job>()

    private fun serviceKey(serviceGroup: String, serviceName: String) = "$serviceGroup.$serviceName"

    @PostConstruct
    fun init() {
        val oneTimeFilters = oneTimeHandlers.flatMap { it.serviceFilters() }
        val oneTimeDups = oneTimeFilters.groupBy { it }.filter { it.value.size > 1 }.keys
        check(oneTimeDups.isEmpty()) { "Duplicate OneTimeTaskHandler service filters: $oneTimeDups" }

        val scheduledFilters = scheduledHandlers.flatMap { it.serviceFilters() }
        val scheduledDups = scheduledFilters.groupBy { it }.filter { it.value.size > 1 }.keys
        check(scheduledDups.isEmpty()) { "Duplicate ScheduledTaskHandler service filters: $scheduledDups" }

        val allServices = (oneTimeFilters + scheduledFilters).distinct()
        if (allServices.isEmpty()) return

        scope.launch {
            allServices.forEach { (sg, sn) ->
                try {
                    val leaseInfo = etcdClient.leaseManager
                        .createOrGetLease(0, dTaskConfig.claimTtl, true, dTaskConfig.claimTtl * 500)
                        .awaitSingle()
                    leaseMap[serviceKey(sg, sn)] = leaseInfo.response.id
                    log.info("TaskCoordinator: lease created for $sg.$sn leaseId=${leaseInfo.response.id}")
                } catch (ex: Exception) {
                    log.warn("TaskCoordinator: failed to create lease for $sg.$sn", ex)
                }
                startForService(sg, sn)
            }
        }

        // Tick loop for scheduled tasks (1 s interval, fires handler per handleRate())
        scope.launch {
            var lastTick = 0L
            val handlerAccuTime = HashMap<ScheduledTaskHandler, Long>().also { map ->
                scheduledHandlers.forEach { map[it] = 0L }
            }
            while (isActive) {
                delay(1000)
                val now = Instant.now().toEpochMilli()
                val delta = if (lastTick == 0L) 0L else now - lastTick
                lastTick = now

                val toFire = scheduledHandlers.filter { handler ->
                    val accu = (handlerAccuTime[handler] ?: 0L) + delta
                    if (accu >= handler.handleRate()) {
                        handlerAccuTime[handler] = 0L
                        true
                    } else {
                        handlerAccuTime[handler] = accu
                        false
                    }
                }

                if (toFire.isEmpty()) continue

                scheduledTaskMap.toMap().forEach { (taskId, entry) ->
                    toFire.forEach { handler ->
                        if (handler.serviceFilters().contains(Pair(entry.serviceGroup, entry.serviceName))) {
                            val existing = activeTickJobs[taskId]
                            if (existing != null && existing.isActive) {
                                log.debug("TaskCoordinator: tick skipped for $taskId, previous tick still running")
                                return@forEach
                            }
                            activeTickJobs[taskId] = scope.launch { tickScheduledTask(taskId, entry, handler) }
                        }
                    }
                }
            }
        }
    }

    private fun startForService(serviceGroup: String, serviceName: String) {
        scope.launch {
            dTaskService.watchServiceTask(serviceGroup, serviceName,
                watchFlux = { event ->
                    if (event.type == Kv.Event.EventType.PUT) {
                        val dTask = DTask.parseFrom(event.kv.value)
                        val wrapper = VersioneWrapper(dTask, event.kv)
                        if (dTask.hasScheduleConfig()) {
                            scope.launch { claimScheduledTask(serviceGroup, serviceName, wrapper) }
                        } else {
                            scope.launch { dispatchOneTimeTask(serviceGroup, serviceName, wrapper) }
                        }
                    } else if (event.type == Kv.Event.EventType.DELETE) {
                        // Task externally deleted (e.g. cancelTask by another process) — notify handler and clean up
                        val dTask = DTask.parseFrom(event.prevKv.value)
                        val taskId = dTask.id
                        val entry = scheduledTaskMap.remove(taskId)
                        activeTickJobs.remove(taskId)
                        if (entry != null) {
                            log.info("TaskCoordinator: scheduled task $taskId externally deleted, calling handleEnd")
                            val handler = scheduledHandlers.firstOrNull {
                                it.serviceFilters().contains(Pair(serviceGroup, serviceName))
                            }
                            if (handler != null) {
                                scope.launch {
                                    try { handler.handleEnd(dTask) }
                                    catch (ex: Exception) { log.warn("TaskCoordinator: handleEnd error on external delete for $taskId", ex) }
                                }
                            }
                        }
                    }
                },
                onDisconnected = {
                    log.warn("TaskCoordinator: etcd disconnected for $serviceGroup.$serviceName — clearing scheduled map")
                    scheduledTaskMap.entries.removeIf { (taskId, entry) ->
                        if (entry.serviceGroup == serviceGroup && entry.serviceName == serviceName) {
                            scope.launch {
                                try { dTaskService.releaseTaskClaim(serviceGroup, serviceName, taskId) }
                                catch (ex: Exception) { log.warn("TaskCoordinator: release on disconnect failed for $taskId", ex) }
                            }
                            true
                        } else false
                    }
                }
            )

            dTaskService.watchClaimKeys(serviceGroup, serviceName) { taskId ->
                scope.launch { handleClaimExpiry(serviceGroup, serviceName, taskId) }
            }

            launch {
                while (isActive) {
                    delay(dTaskConfig.resyncInterval)
                    try {
                        log.debug("TaskCoordinator: resync $serviceGroup.$serviceName")
                        dTaskService.listTasks(serviceGroup, serviceName).forEach { (_, wrapper) ->
                            if (wrapper.value.hasScheduleConfig()) {
                                if (!scheduledTaskMap.containsKey(wrapper.value.id)) {
                                    claimScheduledTask(serviceGroup, serviceName, wrapper)
                                }
                            } else {
                                if (!heldClaims.containsKey(wrapper.value.id)) {
                                    dispatchOneTimeTask(serviceGroup, serviceName, wrapper)
                                }
                            }
                        }
                    } catch (ex: Exception) {
                        log.error("TaskCoordinator: resync error for $serviceGroup.$serviceName", ex)
                    }
                }
            }
        }
    }

    private suspend fun dispatchOneTimeTask(serviceGroup: String, serviceName: String, task: VersioneWrapper<DTask>) {
        val leaseId = leaseMap[serviceKey(serviceGroup, serviceName)] ?: return
        val taskId = task.value.id
        val claimed = dTaskService.claimTask(serviceGroup, serviceName, taskId, leaseId)
        if (!claimed) {
            log.debug("TaskCoordinator: one-time task $taskId already claimed, skipping")
            return
        }
        heldClaims[taskId] = Pair(serviceGroup, serviceName)
        log.info("TaskCoordinator: claimed one-time task $taskId")
        try {
            dTaskService.setTaskStatus(serviceGroup, serviceName, task, TaskStatus.RUNNING)
            val handler = oneTimeHandlers.firstOrNull {
                it.serviceFilters().contains(Pair(serviceGroup, serviceName))
            }
            if (handler != null) {
                try { handler.handle(task.value) }
                catch (ex: Exception) { log.error("TaskCoordinator: one-time handler error for $taskId", ex) }
            }
            dTaskService.cancelTask(serviceGroup, serviceName, taskId)
            log.info("TaskCoordinator: one-time task $taskId completed and deleted")
        } finally {
            dTaskService.releaseTaskClaim(serviceGroup, serviceName, taskId)
            heldClaims.remove(taskId)
        }
    }

    private suspend fun claimScheduledTask(serviceGroup: String, serviceName: String, task: VersioneWrapper<DTask>) {
        val leaseId = leaseMap[serviceKey(serviceGroup, serviceName)] ?: return
        val taskId = task.value.id
        if (scheduledTaskMap.containsKey(taskId)) return
        val claimed = dTaskService.claimTask(serviceGroup, serviceName, taskId, leaseId)
        if (!claimed) {
            log.debug("TaskCoordinator: scheduled task $taskId already claimed by another pod")
            return
        }
        scheduledTaskMap[taskId] = ScheduledTaskEntry(task, serviceGroup, serviceName)
        log.info("TaskCoordinator: claimed scheduled task $taskId for $serviceGroup.$serviceName")
    }

    private suspend fun tickScheduledTask(taskId: String, entry: ScheduledTaskEntry, handler: ScheduledTaskHandler) {
        val now = Instant.now().toEpochMilli()
        val shouldDelete = try {
            val keepGoing = handler.handle(entry.task.value)
            !keepGoing || now > entry.task.value.scheduleConfig.end
        } catch (ex: Exception) {
            log.error("TaskCoordinator: scheduled handler error for $taskId", ex)
            false
        }
        if (shouldDelete) {
            // Remove from maps before cancelTask so the DELETE watch event finds empty entries and skips handleEnd
            scheduledTaskMap.remove(taskId)
            activeTickJobs.remove(taskId)
            try { handler.handleEnd(entry.task.value) } catch (ex: Exception) { log.warn("TaskCoordinator: handleEnd error for $taskId", ex) }
            dTaskService.cancelTask(entry.serviceGroup, entry.serviceName, taskId)
            dTaskService.releaseTaskClaim(entry.serviceGroup, entry.serviceName, taskId)
            log.info("TaskCoordinator: scheduled task $taskId deleted")
        }
    }

    private suspend fun handleClaimExpiry(serviceGroup: String, serviceName: String, taskId: String) {
        if (heldClaims.containsKey(taskId) || scheduledTaskMap.containsKey(taskId)) return
        val task = dTaskService.getTask(serviceGroup, serviceName, taskId) ?: return
        log.info("TaskCoordinator: claim expired for $taskId, attempting handoff")
        if (task.value.hasScheduleConfig()) {
            claimScheduledTask(serviceGroup, serviceName, task)
        } else {
            dispatchOneTimeTask(serviceGroup, serviceName, task)
        }
    }

    @PreDestroy
    fun destroy() {
        runBlocking {
            heldClaims.forEach { (taskId, pair) ->
                try { dTaskService.releaseTaskClaim(pair.first, pair.second, taskId) }
                catch (ex: Exception) { log.warn("TaskCoordinator: failed to release one-time claim $taskId on shutdown", ex) }
            }
            scheduledTaskMap.forEach { (taskId, entry) ->
                try { dTaskService.releaseTaskClaim(entry.serviceGroup, entry.serviceName, taskId) }
                catch (ex: Exception) { log.warn("TaskCoordinator: failed to release scheduled claim $taskId on shutdown", ex) }
            }
        }
        scope.cancel()
    }
}
