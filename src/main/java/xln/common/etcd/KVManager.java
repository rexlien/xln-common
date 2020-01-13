package xln.common.etcd;

import etcdserverpb.KVGrpc;
import etcdserverpb.Rpc;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import xln.common.service.EtcdClient;
import xln.common.utils.FutureUtils;

import java.util.concurrent.TimeUnit;

@Slf4j
public class KVManager {

    private KVGrpc.KVFutureStub stub;
    private EtcdClient client;
    private LeaseManager leaseManager;

    public static class PutOptions {

        //ttl in second
        long ttl = -1;

        //refreshTime in millis
        long refreshTime = -1;

        public PutOptions withTtlSecs(long ttl) {
            this.ttl = ttl;
            return this;
        }

        public PutOptions withRefreshTimeMillis(long refreshTime) {
            this.refreshTime = refreshTime;
            return this;
        }

        static public PutOptions DEFAULT = new PutOptions();
    }

    public KVManager(EtcdClient client, LeaseManager leaseManager) {

        this.client = client;
        this.stub = KVGrpc.newFutureStub(this.client.getChannel()).withDeadlineAfter(client.getTimeoutMillis(), TimeUnit.MILLISECONDS);
        this.leaseManager = leaseManager;

    }


    public Mono<Rpc.PutResponse> put(Rpc.PutRequest request, PutOptions option) {

        if(option.ttl == -1) {
            var response = Mono.fromFuture(FutureUtils.toCompletableFuture(stub.put(request), client.getScheduler()));
            return response;
        }

        long leaseID = request.getLease();
        Mono<LeaseManager.LeaseInfo> leaseInfo;

        if(option.refreshTime == -1) {
            leaseInfo = leaseManager.createLease(leaseID, option.ttl);
        } else {
            leaseInfo = leaseManager.createLease(leaseID, option.ttl, true, option.refreshTime);
        }

        return leaseInfo.flatMap( r -> {

            var newRequest = Rpc.PutRequest.newBuilder().mergeFrom(request).setLease(r.getResponse().getID()).build();
            return Mono.fromFuture(FutureUtils.toCompletableFuture(stub.put(newRequest), client.getScheduler()));

        });
    }

    public Mono<Rpc.DeleteRangeResponse> delete(Rpc.DeleteRangeRequest request) {
        return Mono.fromFuture(FutureUtils.toCompletableFuture(this.stub.deleteRange(request), client.getScheduler()));
    }

    public Mono<Rpc.RangeResponse> get(Rpc.RangeRequest request) {
        return Mono.fromFuture(FutureUtils.toCompletableFuture(this.stub.range(request), client.getScheduler()));
    }
}
