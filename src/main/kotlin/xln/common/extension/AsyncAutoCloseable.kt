package xln.common.extension

interface AsyncAutoCloseable : AutoCloseable {

    @Throws(Exception::class)
    suspend fun closeAsync()
}