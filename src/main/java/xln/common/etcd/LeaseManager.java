package xln.common.etcd;

import etcdserverpb.LeaseGrpc;
import etcdserverpb.Rpc;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import xln.common.grpc.GrpcFluxStream;
import xln.common.service.EtcdClient;
import xln.common.utils.FutureUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;


@Slf4j
public class LeaseManager {

    /*
    public interface Observer {

        void onRemoved(LeaseInfo info);
        void onRefresh(LeaseInfo info);

    }
    */

    public static class LeaseEvent {
        public enum Type {
            ADDED,
            REMOVED,
            REFRESHED
        }

        public LeaseInfo getInfo() {
            return info;
        }

        public LeaseEvent setInfo(LeaseInfo info) {
            this.info = info;
            return this;
        }

        private LeaseInfo info;

        public Type getType() {
            return type;
        }

        public LeaseEvent setType(Type type) {
            this.type = type;
            return this;
        }

        private Type type;
    }


    public static class LeaseInfo {

        public Rpc.LeaseGrantResponse getResponse() {
            return response;
        }

        public LeaseInfo setResponse(Rpc.LeaseGrantResponse response) {
            this.response = response;
            return this;
        }

        public boolean isKeepAlive() {
            return keepAlive;
        }

        public LeaseInfo setKeepAlive(boolean keepAlive) {
            this.keepAlive = keepAlive;
            return this;
        }

        public long getTtlTime() {
            return ttlTime;
        }

        public LeaseInfo setTtlTime(long ttlTime) {
            this.ttlTime = ttlTime;
            return this;
        }

        public long getNextRefreshTime() {
            return nextRefreshTime;
        }

        public LeaseInfo setNextRefreshTime(long nextRefreshTime) {
            this.nextRefreshTime = nextRefreshTime;
            return this;
        }

        public long getRefreshPeriod() {
            return refreshPeriod;
        }

        public LeaseInfo setRefreshPeriod(long refreshPeriod) {
            this.refreshPeriod = refreshPeriod;
            return this;
        }
        public boolean isDeleted() {
            return deleted;
        }

        public LeaseInfo setDeleted(boolean deleted) {
            this.deleted = deleted;
            return this;
        }

        public long getLeaseID() {
            return leaseID;
        }

        public LeaseInfo setLeaseID(long leaseID) {
            this.leaseID = leaseID;
            return this;
        }

        private Rpc.LeaseGrantResponse response;
        private boolean keepAlive = false;
        private long ttlTime = -1;
        private long nextRefreshTime = -1;
        private long refreshPeriod = -1;
        private List<String> effectiveKeys = new ArrayList<>();
        private boolean deleted = false;



        private long leaseID;

    }

    private final EtcdClient client;
    private final ConcurrentHashMap<Long, LeaseInfo> leases = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executorService;
    private final GrpcFluxStream<etcdserverpb.Rpc.LeaseKeepAliveRequest, Rpc.LeaseKeepAliveResponse> keepAliveStream;
    //private volatile Mono<StreamObserver<etcdserverpb.Rpc.LeaseKeepAliveRequest>> keepAliveRequest;
    private final ScheduledFuture keepAliveFuture;
    private volatile long streamTerm = -1L;

    private final EmitterProcessor<LeaseEvent> producer;
    private final FluxSink<LeaseEvent> producerSink;

    public LeaseManager(EtcdClient client) throws Exception {
        this.client = client;
        this.executorService = this.client.getScheduler();

        CompletableFuture<FluxSink<LeaseEvent>> leaseSink = new CompletableFuture<>();

        producer = EmitterProcessor.create();
        //emitterProcessor.


        //this.producer = Flux.<LeaseEvent>create((r) -> {
        //    leaseSink.complete(r);
//
  //      }).publish().autoConnect(0);

        //this.producerSink = leaseSink.get();
        producer.subscribe((r)->{

        });
        this.producerSink = producer.sink();

       this.keepAliveStream = new GrpcFluxStream<>(client.getChannel(), "lease Stream", true) {

           @Override
            public void onNext(Rpc.LeaseKeepAliveResponse value) {

                //log.debug("keep alive received:" + value.getID() );
                leases.computeIfPresent(value.getID(), (k, v) ->{
                    v.ttlTime = System.currentTimeMillis() + value.getTTL() * 1000;
                    return v;
                });
            }

            @Override
            public void onCompleted() {
                super.onCompleted();
                log.info("completed");
            }


        };


        this.keepAliveStream.initStreamSink(()->{
            return LeaseGrpc.newStub(client.getChannel()).leaseKeepAlive(LeaseManager.this.keepAliveStream);
        }).connect();

        this.keepAliveFuture = executorService.scheduleAtFixedRate(() -> {

            var values = leases.values();
            long curTime = System.currentTimeMillis();
            for(var v : values) {

                    if(curTime< v.ttlTime) {
                        if(v.isKeepAlive()) {
                            if(v.getRefreshPeriod() == 0 || curTime > v.getNextRefreshTime()) {
                                //log.debug("do refresh");
                                /*
                                if(this.streamTerm != this.keepAliveStream.getTerm()) {
                                    log.error("stream been closed, reset lease");
                                    removeLease(v);
                                    this.streamTerm = this.keepAliveStream.getTerm();
                                    continue;
                                }

                                 */
                                try {
                                    this.keepAliveStream.getStreamSource().block(Duration.ofSeconds(5)).onNext(Rpc.LeaseKeepAliveRequest.newBuilder().setID(v.getResponse().getID()).build());
                                }catch (Exception ex) {
                                    removeLease(v);
                                    log.error("onOnext update error, remove lease");
                                    continue;
                                }
                                //producerSink.next(new LeaseEvent().setType(LeaseEvent.Type.REFRESHED).setInfo(v));
                                if (v.getRefreshPeriod() != 0) {
                                    v.setNextRefreshTime(curTime + v.getRefreshPeriod());
                                }

                            }
                        }
                    } else {

                        log.debug("emit remove lease");
                        removeLease(v);
                    }
            }

        }, 1000, 1000, TimeUnit.MILLISECONDS);

    }

    private void removeLease(LeaseInfo info) {
        leases.remove(info.response.getID());
        info.setDeleted(true);
        producerSink.next(new LeaseEvent().setType(LeaseEvent.Type.REMOVED).setInfo(info));
    }

    public void shutdown() {
        this.keepAliveFuture.cancel(true);
        keepAliveStream.requestComplete();
        producerSink.complete();
    }


    public Mono<LeaseInfo> createOrGetLease(long leaseID, long ttl)  {

        return createOrGetLease(leaseID, ttl, false);
    }

    public Mono<LeaseInfo> createOrGetLease(long leaseID, long ttl, boolean keepAlive) {

        return createOrGetLease(leaseID, ttl, keepAlive, 0);
    }

    public Mono<LeaseInfo> createOrGetLease(long leaseID, long ttl, boolean keepAlive, long refreshPeriod) {

        if(leaseID != 0) {
            return Mono.just(getLease(leaseID));
        }

        var grantLease = Mono.fromFuture(()->{
            log.debug("call get lease");
            var leaseResponse = LeaseGrpc.newFutureStub(client.getChannel()).withDeadlineAfter(client.getTimeoutMillis(), TimeUnit.MILLISECONDS).
                    leaseGrant(Rpc.LeaseGrantRequest.newBuilder().setID(leaseID).setTTL(ttl).build());
            return FutureUtils.toCompletableFuture(leaseResponse, client.getScheduler());
        });

        return grantLease.map( (r) -> {
            long ttlTime = System.currentTimeMillis() + ttl * 1000;
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
            info.setLeaseID(r.getID());
            leases.put(r.getID(), info);
            this.producerSink.next(new LeaseEvent().setInfo(info).setType(LeaseEvent.Type.ADDED));
            return info;

        });
    }

    public Flux<LeaseEvent> getEventSource() {
        return this.producer;
    }

    public LeaseInfo getLease(long leaseID) {
        return leases.get(leaseID);
    }





}
