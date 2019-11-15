package xln.common.xln.common.extension

import org.redisson.RedissonObject
import org.redisson.RedissonPermitExpirableSemaphore
import org.redisson.RedissonReactive
import org.redisson.RedissonStream
import org.redisson.api.RFuture
import org.redisson.api.RPermitExpirableSemaphoreReactive
import org.redisson.api.RStreamReactive
import org.redisson.api.RedissonReactiveClient
import org.redisson.client.codec.LongCodec
import org.redisson.client.protocol.RedisCommands
import org.redisson.command.CommandAsyncExecutor
import org.redisson.pubsub.SemaphorePubSub
import org.redisson.reactive.ReactiveProxyBuilder
import java.util.*


fun RedissonReactive.getXLNSemaphore(name : String) : RPermitExpirableSemaphoreReactive {
    return ReactiveProxyBuilder.create(commandExecutor, XLNSemaphore(commandExecutor, name), RPermitExpirableSemaphoreReactive::class.java)

}


class XLNSemaphore(commandExecutor: CommandAsyncExecutor?, name: String?) : RedissonPermitExpirableSemaphore(commandExecutor, name) {

    private val commandExecutor: CommandAsyncExecutor = commandExecutor!!

    private fun getChannelName(): String {
        return getChannelName(getName())
    }

    private val timeoutName: String = RedissonObject.suffixName(name, "timeout")
    private val nonExpirableTimeout = 922337203685477L

    override
    fun tryAcquireAsync(permits:Int, timeoutDate:Long) : RFuture<String> {

        if (permits < 0)
        {
            throw IllegalArgumentException("Permits amount can't be negative")
        }

        val id = this.generateId()
        return commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_STRING_DATA,
                "local expiredIds = redis.call('zrangebyscore', KEYS[2], 0, ARGV[4], 'limit', 0, ARGV[1]); " +
                        "if #expiredIds > 0 then " +
                        "redis.call('zrem', KEYS[2], unpack(expiredIds)); " +
                        "local value = redis.call('incrby', KEYS[1], #expiredIds); " +
                        "if tonumber(value) > 0 then " +
                        "redis.call('publish', KEYS[3], value); " +
                        "end;" +
                        "end; " +
                        "local value = redis.call('get', KEYS[1]); " +
                        "if (value == false) then " +
                        "redis.call('zadd', KEYS[2], ARGV[2], ARGV[3]); " +
                        "redis.call('set', KEYS[1], 0); " +
                        "return ARGV[3];" +
                        "end;" +
                        "if (value ~= false and tonumber(value) >= tonumber(ARGV[1])) then " +
                        "redis.call('decrby', KEYS[1], ARGV[1]); " +
                        "redis.call('zadd', KEYS[2], ARGV[2], ARGV[3]); " +
                        "return ARGV[3]; " +
                        "end; " +
                        "local v = redis.call('zrange', KEYS[2], 0, 0, 'WITHSCORES'); " +
                        "if v[1] ~= nil and v[2] ~= ARGV[5] then " +
                        "return ':' .. tostring(v[2]); " +
                        "end " +
                        "return nil;",
                Arrays.asList<Any>(name, timeoutName, getChannelName()), permits, timeoutDate, id, System.currentTimeMillis(), nonExpirableTimeout)

    }

}



