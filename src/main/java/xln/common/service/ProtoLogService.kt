package xln.common.service

import com.google.protobuf.Any
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.Message
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Service
import xln.common.dist.ClusterService
import xln.common.proto.service.RocksLogServiceGrpcKt
import xln.common.store.RocksDBStore
import java.time.Instant
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BiConsumer

@Service
class ProtoLogService(val storageService: StorageService) : ClusterService , RocksLogServiceGrpcKt.RocksLogServiceCoroutineImplBase() {

    private val rocksDBStore: RocksDBStore = storageService.addRocksDB("./protoLog")
    private val uid = AtomicInteger()


    private val scheduler = Executors.newScheduledThreadPool(1)

    init {
        //scheduler.scheduleAtFixedRate( { -> uid.set(0) }
        //,5000, 3000, TimeUnit.MILLISECONDS)
    }

    fun log(any: Any) : Long {

        val timestamp = Instant.now().toEpochMilli()
        val builder = StringBuilder()
        val logID = builder.append(timestamp.toString()).append(":").append(uid.getAndIncrement()).toString()
        rocksDBStore.safePut(logID, any)
        return timestamp
    }

    fun log(msg: Message) : Long {

        val timestamp = Instant.now().toEpochMilli()
        val builder = StringBuilder()
        val logID = builder.append(timestamp.toString()).append(":").append(uid.getAndIncrement()).toString()
        rocksDBStore.safePut(logID, Any.pack(msg))
        return timestamp
    }

    fun iterateLog(consumer: BiConsumer<String?, Any?>) {
        val iterator = rocksDBStore.iterator()
        iterator.seekToFirst()
        while (iterator.isValid) {
            var any: Any? = null
            try {
                any = Any.parseFrom(iterator.value())
            } catch (ex: InvalidProtocolBufferException) {
            }
            if (any != null) {
                consumer.accept(String(iterator.key()), any)
            }
            iterator.next()
        }
    }

    fun iterateLog(consumer: BiConsumer<String?, Any?>, startTime: Long, endTime: Long) {
        val iterator = rocksDBStore.iterator()
        val searchKey = "$startTime"
        val endKey = "${endTime + 1}".toByteArray()
        iterator.seekForPrev(searchKey.toByteArray())
        //skip previous one
        if(iterator.isValid) {
            iterator.next()
        } else {
            iterator.seekToFirst()
        }

        while (iterator.isValid) {
            var any: Any? = null
            if(Arrays.compare(iterator.key(), endKey) >= 0) {
                break
            }

            try {
                any = Any.parseFrom(iterator.value())
            } catch (ex: InvalidProtocolBufferException) {
            }
            if (any != null) {
                consumer.accept(String(iterator.key()), any)
            }
            iterator.next()
        }
    }


    fun deleteLog(key: String) {
        rocksDBStore.delete(key)
    }

    fun clearLog() {

        //93 -> first 2 digits of long's max value
        rocksDBStore.deleteRange("0", "93");
    }


    override suspend fun getLogs(request: xln.common.proto.service.Service.GetLogRequest): xln.common.proto.service.Service.GetLogsResponse {
        //rocksDBStore.iterator().
        return super.getLogs(request)
    }

    override suspend fun getLogCount(request: xln.common.proto.service.Service.GetLogRequest): xln.common.proto.service.Service.GetLogCountResponse {
       return xln.common.proto.service.Service.GetLogCountResponse.newBuilder().setCount(rocksDBStore.count()).build()

    }

}