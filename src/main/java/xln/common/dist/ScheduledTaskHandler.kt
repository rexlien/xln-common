package xln.common.dist

import xln.common.proto.task.DTaskOuterClass.DTask

enum class HandleResult { CONTINUE, DONE }

abstract class ScheduledTaskHandler {
    abstract fun serviceFilters(): List<Pair<String, String>>
    abstract suspend fun handle(dTask: DTask): HandleResult
    open suspend fun handleEnd(dTask: DTask) {}
    open fun handleRate(): Long = 60_000L
}
