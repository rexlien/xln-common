package xln.common.test;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.JsonObjectSerializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import xln.common.cache.CacheController;
import xln.common.proto.command.Command;
import xln.common.service.StorageService;

import javax.validation.constraints.AssertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@Slf4j
public class RocksDBTest {

    @Autowired
    private StorageService storageService;


    @Test
    public void testBasic() {


        //CacheController.CacheInvalidateTask task = new CacheController.CacheInvalidateTask();
        Command.CacheTask task = Command.CacheTask.newBuilder().setCacheManagerName("cacheMgr").setCacheName("cacheName").setKey("cacheKey").build();


        Command.RetryLog log = Command.RetryLog.newBuilder().setTargetName("kafka0").setObj(Any.pack(task)).build();
        storageService.safePut("test", log);
        log = storageService.get("test", Command.RetryLog.parser());
        Any any = log.getObj();

        Assert.assertTrue(log.getTargetName().equals("kafka0"));
        Assert.assertTrue(any.is(Command.CacheTask.class));
        try {
            Command.CacheTask cacheTask = any.unpack(Command.CacheTask.class);
            Assert.assertTrue(cacheTask.getCacheManagerName().equals("cacheMgr"));
            Assert.assertTrue(cacheTask.getCacheName().equals("cacheName"));
            Assert.assertTrue(cacheTask.getKey().equals("cacheKey"));

        }catch (InvalidProtocolBufferException ex) {
            Assert.assertTrue(false);
        }



    }
}
