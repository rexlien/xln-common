package xln.common.dist;

import com.google.protobuf.ByteString;
import etcdserverpb.Rpc;
import lombok.extern.slf4j.Slf4j;
import xln.common.proto.dist.Dist;
import xln.common.service.EtcdClient;


@Slf4j
public class Node {

    private Dist.NodeInfo info;
    private EtcdClient client;
    private boolean self;

    public Node(EtcdClient client, boolean self) {
        this.client = client;
        this.self = self;
    }

    public void refresh(Dist.NodeInfo info) {
        this.info = info;
    }

    public void onShutdown() {
        log.debug("on shutdown");
        if(self) {
            log.debug("delete self:" + info.getKey());
            client.getKvManager().delete(Rpc.DeleteRangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(info.getKey())).build()).block();
        }
    }


}
