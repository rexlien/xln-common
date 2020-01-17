package xln.common.etcd;

import etcdserverpb.Rpc;

public class Response {

    private Rpc.ResponseHeader header;

    public Response(Rpc.ResponseHeader header) {
        this.header = header;
    }

    public long getRevision() {
        return header.getRevision();
    }

}
