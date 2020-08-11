package xln.common.service;


import com.google.protobuf.Message;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.*;
import org.springframework.stereotype.Service;
import xln.common.dist.ClusterService;
import xln.common.proto.command.Command;
import xln.common.store.RocksDBStore;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class StorageService {

    static {
        RocksDB.loadLibrary();
    }

    private ConcurrentHashMap<String, RocksDBStore> rocksDBs = new ConcurrentHashMap<>();
    private volatile Options options;
    private String dbPath = "./rocksDB";

    public StorageService() {

        options = new Options().setCreateIfMissing(true);

    }


    @PreDestroy
    public void destroy() {

        for(Map.Entry<String, RocksDBStore> kv: rocksDBs.entrySet()) {

            kv.getValue().close();
        }

    }

    public RocksDBStore addRocksDB(String filePath) {

        return rocksDBs.computeIfAbsent(filePath, (key) -> {

            RocksDBStore newStore = new RocksDBStore();
            newStore.openDB(filePath, options);
            return newStore;

        });

    }

    public RocksDBStore getRocksDB(String filePath) {
        return rocksDBs.get(filePath);
    }



}
