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
import xln.common.utils.CollectionUtils;

import java.util.HashMap;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@Slf4j
public class UtilTest {

    @Autowired
    private EtcdClient etcdClient;


    @Test
    public void testPathGet() {
        Map<String, Object> testMap = new HashMap<>();
        Map<String, Object> layer2 = new HashMap<>();
       int layer3 = 1;
        testMap.put("layer2", layer2);
        layer2.put("layer3", layer3);

        Object res = (Integer)CollectionUtils.pathGet("layer2/layer3", testMap);
        Assert.assertTrue((Integer)res == 1);

        res = (Integer)CollectionUtils.pathGet("layer2/layer3/layer4", testMap);
        Assert.assertTrue((Integer)res == null);


        res = (Integer)CollectionUtils.pathGet("layer2/layer4", testMap);
        Assert.assertTrue(res == null);

        res = (Integer)CollectionUtils.pathGet("layer2/layer4/layer5", testMap);
        Assert.assertTrue(res == null);


    }

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
}
