package xln.common.dist

import xln.common.proto.task.DTaskOuterClass.DTask

abstract class ScheduledTaskHandler {
    abstract fun serviceFilters(): List<Pair<String, String>>
    // return false to delete the task early (before scheduleConfig.end)
    abstract suspend fun handle(dTask: DTask): Boolean
    open suspend fun handleEnd(dTask: DTask) {}
    open fun handleRate(): Long = 60_000L
}
