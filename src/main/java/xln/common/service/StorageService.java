package xln.common.service;


import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.*;
import org.springframework.stereotype.Service;
import xln.common.proto.command.Command;

import javax.annotation.PostConstruct;
import java.util.Collections;

@Service
@Slf4j
public class StorageService {

    static {
        RocksDB.loadLibrary();
    }

    //private volatile RocksDB rocksDB;
    private final WriteOptions safeWriteOps;
    private volatile Options options;
    private String dbPath = "./rocksDB";

    public StorageService() {

        safeWriteOps = new WriteOptions();
        safeWriteOps.setSync(true);

        options = new Options().setCreateIfMissing(true);

    }

    public boolean safePut(String key, Message obj) {

        try (final RocksDB db = RocksDB.open(options, dbPath)) {
            try {
                db.put(safeWriteOps, key.getBytes(), obj.toByteArray());
            } catch (RocksDBException ex) {
                log.error("", ex);
                return false;
            }
        }catch (RocksDBException ex) {
            log.error("", ex);
            return false;
        }
        return true;
    }

    public <T extends Message> T get(String key, Parser<T> parser) {

        try (final RocksDB db = RocksDB.open(options, dbPath)) {
            try {
                return parser.parseFrom(db.get(key.getBytes()));

            } catch (RocksDBException ex) {
                log.error("", ex);
                return null;
            } catch (InvalidProtocolBufferException ex) {
                log.error("", ex);
                return null;
            }
        }catch (RocksDBException ex) {
            log.error("", ex);
            return null;
        }
    }
}
