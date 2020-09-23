package xln.common.extension

import kotlin.jvm.Throws

interface AsyncAutoCloseable : AutoCloseable {

    @Throws(Exception::class)
    suspend fun closeAsync()
}