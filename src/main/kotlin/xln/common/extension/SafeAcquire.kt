package xln.common.extension

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import reactor.core.publisher.Mono
import xln.common.service.RateLimiter

class SafeAcquire(rateLimiter: RateLimiter, key: String?, millis: Long) : AsyncAutoCloseable {

    val info: Mono<RateLimiter.AcquiredInfo>
    private val rateLimiter: RateLimiter


    override suspend fun closeAsync() {
        rateLimiter.releaseCount(info.awaitSingle().key)
    }

    override fun close() {
        runBlocking {
            closeAsync()
        }
    }

    init {
        info = rateLimiter.acquireCount(key, millis)
        this.rateLimiter = rateLimiter
    }
}
