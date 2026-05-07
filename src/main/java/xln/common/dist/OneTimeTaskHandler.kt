package xln.common.dist

import xln.common.proto.task.DTaskOuterClass.DTask

abstract class OneTimeTaskHandler {
    abstract fun serviceFilters(): List<Pair<String, String>>
    abstract suspend fun handle(dTask: DTask)
}
