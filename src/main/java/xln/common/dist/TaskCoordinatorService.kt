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
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

private val log = KotlinLogging.logger {}

@Service
@ConditionalOnBean(DTaskService::class)
class TaskCoordinatorService(
    private val dTaskService: DTaskService,
    private val dTaskConfig: DTaskConfig,
    private val etcdClient: EtcdClient,
    private val handlers: List<DTaskScheduler.Handler>
) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // serviceKey -> leaseId
    private val leaseMap = ConcurrentHashMap<String, Long>()

    // taskKey -> true (currently claimed by this pod)
    private val heldClaims = ConcurrentHashMap<String, Pair<String, String>>() // taskId -> (serviceGroup, serviceName)

    private fun serviceKey(serviceGroup: String, serviceName: String) = "$serviceGroup.$serviceName"

    @PostConstruct
    fun init() {
        val allFilters = handlers.flatMap { it.serviceFilters() }
        val duplicates = allFilters.groupBy { it }.filter { it.value.size > 1 }.keys
        check(duplicates.isEmpty()) { "Duplicate service filters detected: $duplicates" }
        val services = allFilters.distinct()
        if (services.isEmpty()) return

        scope.launch {
            services.forEach { (sg, sn) ->
                try {
                    val leaseInfo = etcdClient.leaseManager
                        .createOrGetLease(0, dTaskConfig.claimTtl, true, dTaskConfig.claimTtl * 500)
                        .awaitSingle()
                    leaseMap[serviceKey(sg, sn)] = leaseInfo.response.id
                    log.info("TaskCoordinator: lease created for $sg.$sn leaseId=${leaseInfo.response.id}")
                } catch (ex: Exception) {
                    log.warn("TaskCoordinator: failed to create lease for $sg.$sn, will retry on reconnect", ex)
                }
                startForService(sg, sn)
            }
        }
    }

    private fun startForService(serviceGroup: String, serviceName: String) {
        scope.launch {
            // Watch tasks — handle one-time tasks directly
            dTaskService.watchServiceTask(serviceGroup, serviceName,
                watchFlux = { event ->
                    if (event.type == Kv.Event.EventType.PUT) {
                        val dTask = DTask.parseFrom(event.kv.value)
                        if (!dTask.hasScheduleConfig()) {
                            val wrapper = VersioneWrapper(dTask, event.kv)
                            scope.launch { dispatchOneTimeTask(serviceGroup, serviceName, wrapper) }
                        }
                    }
                },
                onDisconnected = {
                    log.warn("TaskCoordinator: etcd disconnected for $serviceGroup.$serviceName")
                }
            )

            // Watch claim key deletions for handoff
            dTaskService.watchClaimKeys(serviceGroup, serviceName) { taskId ->
                scope.launch { handleClaimExpiry(serviceGroup, serviceName, taskId) }
            }

            // Resync loop
            launch {
                while (isActive) {
                    delay(dTaskConfig.resyncInterval)
                    try {
                        log.debug("TaskCoordinator: resync $serviceGroup.$serviceName")
                        dTaskService.listTasks(serviceGroup, serviceName).forEach { (_, wrapper) ->
                            if (!wrapper.value.hasScheduleConfig()) {
                                dispatchOneTimeTask(serviceGroup, serviceName, wrapper)
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
            log.debug("TaskCoordinator: task $taskId already claimed, skipping")
            return
        }

        heldClaims[taskId] = Pair(serviceGroup, serviceName)
        log.info("TaskCoordinator: claimed one-time task $taskId for $serviceGroup.$serviceName")

        try {
            dTaskService.setTaskStatus(serviceGroup, serviceName, task, TaskStatus.RUNNING)

            val handler = handlers.firstOrNull { it.serviceFilters().contains(Pair(serviceGroup, serviceName)) }
            if (handler != null) {
                try {
                    handler.handle(task.value)
                } catch (ex: Exception) {
                    log.error("TaskCoordinator: handler error for task $taskId", ex)
                }
            }

            // cancelTask deletes task + progress + state unconditionally (no version check).
            // versionDeleteTask would fail here because setTaskStatus(RUNNING) already bumped the version.
            dTaskService.cancelTask(serviceGroup, serviceName, taskId)
            log.info("TaskCoordinator: one-time task $taskId completed and deleted")
        } finally {
            // Release claim last so watchClaimKeys fires while heldClaims still contains the entry,
            // preventing handleClaimExpiry from re-dispatching a task we just finished.
            dTaskService.releaseTaskClaim(serviceGroup, serviceName, taskId)
            heldClaims.remove(taskId)
        }
    }

    private suspend fun handleClaimExpiry(serviceGroup: String, serviceName: String, taskId: String) {
        if (heldClaims.containsKey(taskId)) return

        val task = dTaskService.getTask(serviceGroup, serviceName, taskId) ?: return
        if (task.value.hasScheduleConfig()) return

        log.info("TaskCoordinator: claim expired for task $taskId, attempting handoff")
        dispatchOneTimeTask(serviceGroup, serviceName, task)
    }

    @PreDestroy
    fun destroy() {
        runBlocking {
            heldClaims.forEach { (taskId, pair) ->
                try {
                    dTaskService.releaseTaskClaim(pair.first, pair.second, taskId)
                } catch (ex: Exception) {
                    log.warn("TaskCoordinator: failed to release claim for $taskId on shutdown", ex)
                }
            }
        }
        scope.cancel()
    }
}
