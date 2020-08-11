package xln.common.store;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.*;


@Slf4j
public class RocksDBStore {

    private static WriteOptions safeWriteOps = new WriteOptions().setSync(true);

    private volatile Options options;
    private String path;
    private volatile RocksDB rocksDB;

    public RocksDBStore() {

    }

    public void openDB(String path, Options option) {

        this.path = path;
        this.options = option;

        try  {
            this.rocksDB = RocksDB.open(options, path);
        }catch (RocksDBException ex) {
            log.error("", ex);

        }
    }

    public void close() {
        rocksDB.close();
    }


    public boolean safePut(String key, Message obj) {

        try {
            rocksDB.put(safeWriteOps, key.getBytes(), obj.toByteArray());
        } catch (RocksDBException ex) {
            log.error("", ex);
            return false;
        }

        return true;
    }

    public <T extends Message> T get(String key, Parser<T> parser) {

        try {

            T ret =  parser.parseFrom(rocksDB.get(key.getBytes()));
            return ret;

        } catch (RocksDBException ex) {
            log.error("", ex);
            return null;
        } catch (InvalidProtocolBufferException ex) {
            log.error("", ex);
            return null;
        }

    }

    public void deleteRange(String startKey, String endKey) {
        try {
            rocksDB.deleteRange(startKey.getBytes(), endKey.getBytes());
        }catch (RocksDBException ex) {
            log.error("", ex);
        }
    }

    public void delete(String key) {

        try {
            rocksDB.delete(key.getBytes());
        }catch (RocksDBException ex) {
            log.error("", ex);
        }
    }

    public long count() {
        try {
            return rocksDB.getLongProperty("rocksdb.estimate-num-keys");
        }catch (RocksDBException ex) {
            return -1L;
        }
    }

    public RocksIterator iterator() {
        return rocksDB.newIterator();
    }

}
