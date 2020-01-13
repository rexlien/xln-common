package xln.common.dist;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import etcdserverpb.KVGrpc;
import etcdserverpb.LeaseGrpc;
import etcdserverpb.Rpc;
import javassist.bytecode.ByteArray;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import xln.common.config.CommonConfig;
import xln.common.etcd.KVManager;
import xln.common.proto.dist.Dist;
import xln.common.service.EtcdClient;
import xln.common.utils.NetUtils;

import javax.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static xln.common.utils.NetUtils.getHostAddress;

@Slf4j
@Service
@ConditionalOnBean(EtcdClient.class)
public class Cluster {

    private final Map<String, Node> nodes = new HashMap<>();
    private final CommonConfig commonConfig;
    private final EtcdClient etcdClient;

    private Random random = new Random();


    public Cluster(CommonConfig commonConfig, EtcdClient etcdClient) {
        this.commonConfig = commonConfig;
        this.etcdClient = etcdClient;

        startup();

    }

    public String getNodeKey() {

        var appName = commonConfig.getAppName();
        byte[] randoms = new byte[4];
        random.nextBytes(randoms);
        var host = NetUtils.getHostName() + "-" + NetUtils.getHostAddress() + "-" + Base64.getEncoder().encodeToString(randoms);
        return "apps/" + appName + "/" + host;
    }

    @PreDestroy
    private void destroy() {
        for(var kv : nodes.entrySet()) {
            kv.getValue().onShutdown();
        }
    }

    private void startup() {
        var nodeKey = getNodeKey();
        var nodeInfo = Dist.NodeInfo.newBuilder().setKey(nodeKey).setName(NetUtils.getHostName()).setAddress(getHostAddress()).build();
        var putRequest = etcdserverpb.Rpc.PutRequest.newBuilder().setKey(ByteString.copyFromUtf8(nodeKey)).
                setValue(nodeInfo.toByteString()).build();

        var response = etcdClient.getKvManager().put(putRequest, new KVManager.PutOptions().withTtlSecs(20).withRefreshTimeMillis(5000)).block();
        var node = new Node(etcdClient,true);
        node.refresh(nodeInfo);
        nodes.put(nodeInfo.getKey(), node);

        log.info("self node created:" + response.toString());

    }








}
