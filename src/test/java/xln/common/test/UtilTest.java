package xln.common.test;

import com.google.protobuf.ByteString;
import etcdserverpb.KVGrpc;
import etcdserverpb.Rpc;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import xln.common.service.EtcdClient;
import xln.common.utils.RandomUtils;

import java.util.HashMap;
import java.util.Random;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@Slf4j
public class UtilTest {

    @Autowired
    private EtcdClient etcdClient;




    @Test
    public void testEtcd() throws Exception {

        var channel = etcdClient.getChannel();
        var futureStub = KVGrpc.newFutureStub(channel);//.withDeadlineAfter(10, TimeUnit.SECONDS);


        for(int i = 0; i < 10; i++) {
            var putRequest = etcdserverpb.Rpc.PutRequest.newBuilder().setKey(ByteString.copyFromUtf8("TestKey:" + i)).
                    setValue(ByteString.copyFromUtf8("TestValue2")).setPrevKv(true).build();
            var future = futureStub.put(putRequest);
            var response = future.get();
            log.info(response.toString());
        }
        var getRequest = etcdserverpb.Rpc.RangeRequest.newBuilder().setKey(ByteString.copyFromUtf8("TestKey")).
                setRangeEnd(ByteString.copyFromUtf8("TestKez")).build();
        var getFuture = futureStub.range(getRequest);
        log.info(getFuture.get().toString());




        getRequest = etcdserverpb.Rpc.RangeRequest.newBuilder().setKey(ByteString.copyFromUtf8("TestKey:0")).build();
        getFuture = getFuture = futureStub.range(getRequest);
        var kv = getFuture.get().getKvs(0);

        var version = kv.getVersion();
        var putRequest = etcdserverpb.Rpc.PutRequest.newBuilder().setKey(ByteString.copyFromUtf8("TestKey:0")).
                setValue(ByteString.copyFromUtf8("Success")).build();

        var txnRequest = Rpc.TxnRequest.newBuilder().addCompare(Rpc.Compare.newBuilder().
                setKey(ByteString.copyFromUtf8("TestKey:0")).setVersion(version -1).setResult(Rpc.Compare.CompareResult.EQUAL)
                .build()).build();
        var txnFuture = futureStub.txn(txnRequest);
        var txnResponse = txnFuture.get();
        log.info(Boolean.toString(txnResponse.getSucceeded()));
        log.info(txnResponse.toString());

        txnRequest = Rpc.TxnRequest.newBuilder().addCompare(Rpc.Compare.newBuilder().
                setKey(ByteString.copyFromUtf8("TestKey:0")).setVersion(version).setResult(Rpc.Compare.CompareResult.EQUAL)
                .build()).addSuccess(Rpc.RequestOp.newBuilder().setRequestPut(putRequest).build()).build();
        txnFuture = futureStub.txn(txnRequest);
        txnResponse = txnFuture.get();
        //log.info(txnResponse.getSucceeded())
        log.info(Boolean.toString(txnResponse.getSucceeded()));
        log.info(txnResponse.toString());

        //log.info(txnFuture.get().getResponsesList().toString());


    }

    @Test
    public void testEtcdInc() {

        etcdClient.getKvManager().delete("atomicCount").block();

        var response = etcdClient.getKvManager().inc("atomicCount");
        log.info(response.block().toString());

        response = etcdClient.getKvManager().inc("atomicCount");
        log.info(response.block().toString());

    }


    @Test
    public void testRandom() {

        var hashmap = new HashMap<Long, Long>();



        for(int j = 0; j < 100; j++) {

            hashmap.clear();
            var base = new Random().nextInt();
            var offset = new Random().nextInt();
            long prevOrder = -1;
            boolean first = true;
            for (int i = 0; i < 256; i++) {
                long order = RandomUtils.randomSeq(offset, i, base, 256);
                if(first && prevOrder != -1) {
                    log.info("diff:" + String.valueOf((order + 256 - prevOrder) % 256));
                    first = false;
                }

                if (order < 0 || hashmap.containsKey(order)) {
                    log.error("error: " + order);
                    Assert.fail();

                }
                hashmap.put(order, order);
                prevOrder = order;
            }
        }




    }

}
