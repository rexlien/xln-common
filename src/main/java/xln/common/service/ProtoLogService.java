package xln.common.service;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksIterator;
import org.springframework.stereotype.Service;
import xln.common.proto.command.Command;
import xln.common.service.StorageService;
import xln.common.store.RocksDBStore;

import javax.annotation.PreDestroy;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

@Service
@Slf4j
public class ProtoLogService {

    private final RocksDBStore rocksDBStore;

    private AtomicInteger uid = new AtomicInteger();


    public ProtoLogService(StorageService storageService) {

        rocksDBStore = storageService.addRocksDB("./protoLog");
    }

    public void log(Any any) {

        StringBuilder builder = new StringBuilder();
        String logID = builder.append(String.valueOf(Instant.now().toEpochMilli())).append(uid.getAndIncrement()).toString();
        rocksDBStore.safePut(logID, any);
    }

    public void log(Message msg) {

        StringBuilder builder = new StringBuilder();
        String logID = builder.append(String.valueOf(Instant.now().toEpochMilli())).append(uid.getAndIncrement()).toString();
        rocksDBStore.safePut(logID, Any.pack(msg));
    }

    public void iterateLog(BiConsumer<String, Any> consumer) {

        RocksIterator iterator = rocksDBStore.iterator();
        iterator.seekToFirst();
        while( iterator.isValid()) {

            Any any = null;
            try {
                any = Any.parseFrom(iterator.value());

            } catch (InvalidProtocolBufferException ex) {

            }
            if(any != null) {
                consumer.accept(new String(iterator.key()), any);
            }

            iterator.next();
        }
    }

    public void deleteLog(String key) {
        rocksDBStore.delete(key);
    }

}
