package xln.common.dist;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import etcdserverpb.Rpc;
import lombok.extern.slf4j.Slf4j;
import mvccpb.Kv;
import xln.common.etcd.KVManager;
import xln.common.proto.dist.Dist;
import xln.common.service.EtcdClient;

import java.time.Duration;


@Slf4j
public class Node {

    public Dist.NodeInfo getInfo() {
        return info;
    }

    private Dist.NodeInfo info;
    private Cluster cluster;
    private boolean self;
    private String storeKey;

    public long getVersion() {
        return version;
    }

    public long getModRevision() {
        return modRevision;
    }

    public long getCreateRevision() {
        return createRevision;
    }

    private long version;
    private long modRevision;
    private long createRevision;

    public Node(Cluster cluster, Dist.NodeInfo info) {
        this.cluster = cluster;;
        this.info = info;
        storeKey = info.getKey();

    }

    public Node(Cluster cluster, ByteString info)  {

        this.cluster = cluster;
        try {
            this.info = Dist.NodeInfo.parseFrom(info);
            storeKey = this.info.getKey();
        }catch(InvalidProtocolBufferException ex) {
            log.error("", ex);
        }

    }

    public Node(Cluster cluster, Kv.KeyValue keyValue){
        this(cluster, keyValue.getValue());

        this.modRevision = keyValue.getModRevision();
        this.version = keyValue.getVersion();
        this.createRevision = keyValue.getCreateRevision();
        this.storeKey = keyValue.getKey().toStringUtf8();


    }

    public Node setSelf() {
        this.self = true;
        return this;
    }

    public void refresh(Dist.NodeInfo info) {
        this.info = info;
    }

    public void onShutdown() {
        //log.debug("on shutdown");
        if(self && info != null) {
            log.debug("delete self:" + storeKey);
            try {
                if(createRevision != 0) {
                    log.debug("delete create revision:" + createRevision );
                    var txn = cluster.getEtcdClient().getKvManager().transactDelete(Rpc.DeleteRangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(storeKey)).build(),
                            new KVManager.TransactOptions().withCheckedCreateRevision(createRevision)).block(Duration.ofSeconds(3));
                    log.debug("transact delete:" + Boolean.toString(txn.getSucceeded()));

                } else {
                    cluster.getEtcdClient().getKvManager().delete(Rpc.DeleteRangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(storeKey)).build()).block(Duration.ofSeconds(3));
                }
            }catch (RuntimeException ex) {
                log.warn("timeout when shutdown", ex);
            }
        }
    }


}
