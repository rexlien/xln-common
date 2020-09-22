package xln.common.etcd;

import com.google.protobuf.ByteString;
import etcdserverpb.KVGrpc;
import etcdserverpb.Rpc;
import etcdserverpb.WatchGrpc;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import xln.common.dist.KeyUtils;
import xln.common.service.EtcdClient;
import xln.common.utils.FutureUtils;

import java.util.concurrent.TimeUnit;

@Slf4j
public class KVManager {

    private KVGrpc.KVFutureStub stub;
    private EtcdClient client;
    private LeaseManager leaseManager;

    public static class PutOptions {


        public String getKey() {
            return key;
        }

        public PutOptions withKey(String key) {
            this.key = key;
            return this;
        }

        String key;

        public ByteString getValue() {
            return value;
        }

        public PutOptions withValue(ByteString value) {
            this.value = value;
            return this;
        }

        ByteString value;


        public boolean isPrevKV() {
            return prevKV;
        }

        public PutOptions withPrevKV(boolean prevKV) {
            this.prevKV = prevKV;
            return this;
        }

        boolean prevKV;

        //ttl in second
        long ttl = -1;

        public long getLeaseID() {
            return leaseID;
        }

        public PutOptions withLeaseID(long leaseID) {
            this.leaseID = leaseID;
            return this;
        }

        long leaseID = 0;

        //refreshTime in millis
        long refreshTime = -1;

        boolean ifAbsent = false;

        public PutOptions withTtlSecs(long ttl) {
            this.ttl = ttl;
            return this;
        }

        public PutOptions withRefreshTimeMillis(long refreshTime) {
            this.refreshTime = refreshTime;
            return this;
        }


        public PutOptions withIfAbsent(boolean ifAbsent) {
            this.ifAbsent = ifAbsent;
            return this;
        }

        static public PutOptions DEFAULT = new PutOptions();
    }

    public static class TransactOptions {

        public long getCheckedCreateRevision() {
            return this.checkedCreateRevision;
        }

        public TransactOptions withCheckedCreateRevision(long checkedCreateRevision) {
            this.checkedCreateRevision = checkedCreateRevision;
            return this;
        }

        private long checkedCreateRevision;

    }



    public KVManager(EtcdClient client, LeaseManager leaseManager) {

        this.client = client;
        this.stub = KVGrpc.newFutureStub(this.client.getChannel());
        this.leaseManager = leaseManager;

    }

    private Mono<LeaseManager.LeaseInfo> createOrGetLease(long leaseID, PutOptions options) {

        Mono<LeaseManager.LeaseInfo> leaseInfo;
        if(options.refreshTime == -1) {
            leaseInfo = leaseManager.createOrGetLease(leaseID, options.ttl);
        } else {
            leaseInfo = leaseManager.createOrGetLease(leaseID, options.ttl, true, options.refreshTime);
        }
        return leaseInfo;
    }

    public Mono<Rpc.PutRequest> createRequest(PutOptions options) {

        var builder = Rpc.PutRequest.newBuilder().setKey(ByteString.copyFromUtf8(options.key)).setValue((options.getValue()))
                .setPrevKv(options.prevKV);
        if(options.leaseID == 0 && options.ttl != -1) {
            return createOrGetLease(0, options).map((r) -> {
                  return builder.setLease(r.getResponse().getID()).build();
                }
            );

        } else {
           if(options.leaseID != 0) {
               return Mono.just(builder.setLease(options.leaseID).build());
           } else  {
               return Mono.just(builder.build());
           }
        }

    }

    public static Rpc.RangeRequest createDirectoryRangeRequest(String directory) {
        return Rpc.RangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(directory)).
                setRangeEnd(ByteString.copyFromUtf8(KeyUtils.getEndKey(directory))).build();
    }


    public Mono<Rpc.PutResponse> put(PutOptions option) {

        var requestMono = createRequest(option);
        return requestMono.flatMap( r -> {
            return Mono.fromFuture(FutureUtils.toCompletableFuture(stub.put(r), client.getScheduler()));

        });
    }

    public Mono<Rpc.PutResponse> put(String key, ByteString value) {

        var putOption = new PutOptions();
        putOption.key = key;
        putOption.value = value;
        var requestMono = createRequest(putOption);
        return requestMono.flatMap( r -> {
            return Mono.fromFuture(FutureUtils.toCompletableFuture(stub.put(r), client.getScheduler()));

        });
    }


    public Mono<Rpc.DeleteRangeResponse> delete(Rpc.DeleteRangeRequest request) {
        return Mono.fromFuture(FutureUtils.toCompletableFuture(this.stub.deleteRange(request), client.getScheduler()));
    }

    public Mono<Rpc.RangeResponse> get(Rpc.RangeRequest request) {
        return Mono.fromFuture(FutureUtils.toCompletableFuture(this.stub.range(request), client.getScheduler()));
    }

    public Mono<ByteString> get(String key) {

        Rpc.RangeRequest request = Rpc.RangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(key)).build();
        return Mono.fromFuture(FutureUtils.toCompletableFuture(this.stub.range(request), client.getScheduler())).flatMap(
                r -> {
                    if(r.getCount() == 0) {
                        return Mono.empty();
                    }
                    return Mono.just(r.getKvs(0).getValue());
                }
        );


    }


    public Mono<Rpc.TxnResponse> transactDelete(Rpc.DeleteRangeRequest request, TransactOptions options) {

        log.debug("transact delete:" + request.getKey() + "-" + options.getCheckedCreateRevision());

        var txnBuilder = Rpc.TxnRequest.newBuilder();

        txnBuilder.addCompare(Rpc.Compare.newBuilder().setTarget(Rpc.Compare.CompareTarget.CREATE).
                setKey(request.getKey()).setCreateRevision(options.getCheckedCreateRevision()).setResult(Rpc.Compare.CompareResult.EQUAL)
                .build());

        var txnRequest = txnBuilder.addSuccess(Rpc.RequestOp.newBuilder().setRequestDeleteRange(request)).build();
        var future = FutureUtils.toCompletableFuture(stub.txn(txnRequest), client.getScheduler());
        return Mono.fromFuture(future);

    }



    public Mono<Rpc.TxnResponse> transactPut(PutOptions options) {

        var request = createRequest(options);
        var txnBuilder = Rpc.TxnRequest.newBuilder();
        if(options.ifAbsent) {
            txnBuilder.addCompare(Rpc.Compare.newBuilder().setTarget(Rpc.Compare.CompareTarget.CREATE).
                    setKey(ByteString.copyFromUtf8(options.getKey())).setCreateRevision(0).setResult(Rpc.Compare.CompareResult.EQUAL)
                    .build());
        }

        return request.flatMap((r)-> {

            var future = stub.txn(txnBuilder.addSuccess(Rpc.RequestOp.newBuilder().setRequestPut(r)).build());
            return Mono.fromFuture(FutureUtils.toCompletableFuture(future, client.getScheduler()));

        });


        //stub.txn(Rpc.TxnRequest.newBuilder().setCompare().build())

    }
}
