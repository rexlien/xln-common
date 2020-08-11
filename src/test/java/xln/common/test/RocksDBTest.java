package xln.common.test;

import com.google.common.primitives.UnsignedBytes;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.StringValue;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.rocksdb.Options;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.JsonObjectSerializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import xln.common.cache.CacheController;
import xln.common.proto.command.Command;
import xln.common.service.ProtoLogService;
import xln.common.service.StorageService;
import xln.common.store.RocksDBStore;

import java.util.Arrays;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@Slf4j
public class RocksDBTest {

    @Autowired
    private ProtoLogService protoLogService;


    @Test
    public void testBasic() {

        //CacheController.CacheInvalidateTask task = new CacheController.CacheInvalidateTask();
        Command.CacheTask task = Command.CacheTask.newBuilder().setCacheManagerName(StringValue.of("cacheMgr")).setCacheName(StringValue.of("cacheName")).setKey(StringValue.of("cacheKey")).build();

        Command.Retry log = Command.Retry.newBuilder().setPath("kafka://kafka0").setObj(Any.pack(task)).build();
        RocksDBStore store = new RocksDBStore();
        store.openDB("./rocksDBTest", new Options().setCreateIfMissing(true));
        store.safePut("test", log);
        log = store.get("test", Command.Retry.parser());
        Any any = log.getObj();

        Assert.assertTrue(log.getPath().equals("kafka://kafka0"));
        Assert.assertTrue(any.is(Command.CacheTask.class));
        try {
            Command.CacheTask cacheTask = any.unpack(Command.CacheTask.class);
            Assert.assertTrue(cacheTask.getCacheManagerName().getValue().equals("cacheMgr"));
            Assert.assertTrue(cacheTask.getCacheName().getValue().equals("cacheName"));
            Assert.assertTrue(cacheTask.getKey().getValue().equals("cacheKey"));

        }catch (InvalidProtocolBufferException ex) {
            Assert.assertTrue(false);
        }

    }

    @Test
    public void testIterate() throws Exception {

        protoLogService.clearLog();
        Command.CacheTask task = Command.CacheTask.newBuilder().setCacheManagerName(StringValue.of("cacheMgr")).setCacheName(StringValue.of("cacheName")).setKey(StringValue.of("cacheKey")).build();

        Command.Retry retryLog = Command.Retry.newBuilder().setPath("kafka://kafka0").setObj(Any.pack(task)).build();
        protoLogService.log(retryLog);

        Thread.sleep(1000);

        long startTime = protoLogService.log(retryLog);

        Thread.sleep(1000);

        long endTime = protoLogService.log(retryLog);

        protoLogService.iterateLog( (k, v) -> {
            log.info(k);
        }, startTime, endTime);



        startTime = protoLogService.log(retryLog);

        Thread.sleep(1000);

        endTime = protoLogService.log(retryLog);

        Thread.sleep(1000);

        long lastLogTime = protoLogService.log(retryLog);

        protoLogService.iterateLog( (k, v) -> {
            log.info(k);
        }, startTime, endTime);

        protoLogService.iterateLog( (k, v) -> {
            log.info(k);
        },0, lastLogTime);
    }

    @Test
    public void testKeyCompare() {

        String a = "1523:1";
        String b = "1523:112";

        Assert.assertTrue(Arrays.compare(a.getBytes(), b.getBytes()) < 0);

        var comparator = UnsignedBytes.lexicographicalComparator();
        Assert.assertTrue(comparator.compare(a.getBytes(), b.getBytes()) < 0);

        String c = "1522";
        Assert.assertTrue(Arrays.compare(c.getBytes(), b.getBytes()) < 0);

        String d = "1524";
        Assert.assertTrue(Arrays.compare(b.getBytes(), d.getBytes()) < 0);
        
    }



}
