package xln.common.etcd;

import com.google.common.util.concurrent.ListenableFuture;
import etcdserverpb.LeaseGrpc;
import etcdserverpb.Rpc;
import io.grpc.stub.StreamObserver;
import io.netty.util.internal.SystemPropertyUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.concurrent.CompletableToListenableFutureAdapter;
import org.springframework.util.concurrent.ListenableFutureAdapter;
import reactor.core.publisher.Mono;
import xln.common.service.EtcdClient;
import xln.common.utils.FutureUtils;

import java.rmi.dgc.Lease;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


@Slf4j
public class LeaseManager {

    @Data
    public static class LeaseInfo {
        private Rpc.LeaseGrantResponse response;
        private boolean keepAlive = false;
        private long ttlTime = -1;
        private long nextRefreshTime = -1;
        private long refreshPeriod = -1;
        private List<String> effectiveKeys = new ArrayList<>();

    }

    private final EtcdClient client;
    private final ConcurrentHashMap<Long, LeaseInfo> leases = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executorService;
    private final StreamObserver<Rpc.LeaseKeepAliveResponse> keepAliveResponse;
    private final StreamObserver<etcdserverpb.Rpc.LeaseKeepAliveRequest> keepAliveRequest;
    private final ScheduledFuture keepAliveFuture;


    public LeaseManager(EtcdClient client) {
        this.client = client;
        this.executorService = this.client.getScheduler();


        //start keep alive
       this.keepAliveResponse = new StreamObserver<>() {
            @Override
            public void onNext(Rpc.LeaseKeepAliveResponse value) {

                //log.debug("keep alive received:" + value.getID() );
                leases.computeIfPresent(value.getID(), (k, v) ->{
                    v.ttlTime = System.currentTimeMillis() + value.getTTL() * 1000;
                    return v;
                });
            }

            @Override
            public void onError(Throwable t) {
                log.error("", t);
            }

            @Override
            public void onCompleted() {
                log.info("completed");
            }
        };
        this.keepAliveRequest = LeaseGrpc.newStub(client.getChannel()).leaseKeepAlive(keepAliveResponse);

        this.keepAliveFuture = executorService.scheduleAtFixedRate(() -> {

            var values = leases.values();
            long curTime = System.currentTimeMillis();
            for(var v : values) {

                    if(curTime< v.ttlTime) {
                        if(v.isKeepAlive()) {
                            if(v.getRefreshPeriod() == 0 || curTime > v.getNextRefreshTime()) {
                                //log.debug("do refresh");
                                this.keepAliveRequest.onNext(Rpc.LeaseKeepAliveRequest.newBuilder().setID(v.getResponse().getID()).build());
                                if(v.getRefreshPeriod() != 0) {
                                    v.setNextRefreshTime(curTime + v.getRefreshPeriod());
                                }
                            }
                        }
                    } else {
                        leases.remove(v.response.getID());
                    }
            }

        }, 1000, 1000, TimeUnit.MILLISECONDS);

    }

    public void shutdown() {
        this.keepAliveFuture.cancel(true);
        keepAliveResponse.onCompleted();
        keepAliveRequest.onCompleted();
    }


    public Mono<LeaseInfo> createLease(long leaseID, long ttl)  {

        return createLease(leaseID, ttl, false);
    }

    public Mono<LeaseInfo> createLease(long leaseID, long ttl, boolean keepAlive) {

        return createLease(leaseID, ttl, keepAlive, 0);
    }

    public Mono<LeaseInfo> createLease(long leaseID, long ttl, boolean keepAlive, long refreshPeriod) {
        long ttlTime = System.currentTimeMillis() + ttl * 1000;

        var leaseResponse = LeaseGrpc.newFutureStub(client.getChannel()).withDeadlineAfter(client.getTimeoutMillis(), TimeUnit.MILLISECONDS).
                leaseGrant(Rpc.LeaseGrantRequest.newBuilder().setID(leaseID).setTTL(ttl).build());
        var grantLease = Mono.fromFuture(FutureUtils.toCompletableFuture(leaseResponse, client.getScheduler()));

        return grantLease.map( (r) -> {
            LeaseInfo info = new LeaseInfo();
            if(!r.getError().isEmpty()) {
                info.setResponse(r);
                return info;
            }
            info.setKeepAlive(keepAlive);
            info.setRefreshPeriod(refreshPeriod);
            info.setNextRefreshTime(System.currentTimeMillis() + refreshPeriod);
            info.setTtlTime( ttlTime);
            info.setResponse(r);
            leases.put(r.getID(), info);
            return info;

        });
    }

    public LeaseInfo getLease(long leaseID) {
        return leases.get(leaseID);
    }





}
