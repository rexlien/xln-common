package xln.common.etcd;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import etcdserverpb.KVGrpc;
import etcdserverpb.Rpc;
import lombok.extern.slf4j.Slf4j;
import mvccpb.Kv;
import reactor.core.publisher.Mono;
import xln.common.dist.KeyUtils;
import xln.common.service.EtcdClient;
import xln.common.utils.FutureUtils;
import xln.common.utils.ProtoUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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

        public PutOptions withMessage(Message message) {

            //var json = ProtoUtils.json(message);
            //if(json == null) {
              //  throw new RuntimeException();
            //}
            return withValue(message.toByteString());//ByteString.copyFromUtf8(json));
        }


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

       // boolean ifAbsent = false;

        public PutOptions withTtlSecs(long ttl) {
            this.ttl = ttl;
            return this;
        }

        public PutOptions withRefreshTimeMillis(long refreshTime) {
            this.refreshTime = refreshTime;
            return this;
        }

/*
        public PutOptions withIfAbsent(boolean ifAbsent) {
            this.ifAbsent = ifAbsent;
            return this;
        }
*/
        static public PutOptions DEFAULT = new PutOptions();
    }

    public static class TransactDelete {


        //public long getCheckedCreateRevision() {
          //  return this.currentCreateRevision;
        //}

        public TransactDelete enableCompareCreateRevision(long currentCreateRevision) {
            this.compareCreateRevision = true;
            this.currentCreateRevision = currentCreateRevision;
            return this;
        }

        private boolean compareCreateRevision = false;
        private long currentCreateRevision = -1;


        public TransactDelete enableCompareVersion(long currentVersion) {
            this.compareVersion = true;
            this.currentVersion = currentVersion;
            return this;
        }


        private boolean compareVersion = false;
        private long currentVersion = -1;

    }

    public static class TransactPut {

        private Rpc.TxnRequest.Builder txnBuilder = Rpc.TxnRequest.newBuilder();
        private PutOptions put;

        public TransactPut(PutOptions put) {

            this.put = put;
        }

        private boolean ifAbsent = false;
        private boolean ifSameVersion = false;

        public PutOptions getPut() {
            return this.put;
        }

        public Rpc.TxnRequest.Builder getTxnBuilder() {
            return this.txnBuilder;
        }

        public Mono<Rpc.TxnRequest> buildTxnRequest(KVManager kvManager) {

            var request = kvManager.createRequest(put);
            return request.flatMap( r -> {
                return Mono.just(txnBuilder.addSuccess(Rpc.RequestOp.newBuilder().setRequestPut(r)).build());
                //return Mono.<Rpc.Txn>fromFuture(FutureUtils.toCompletableFuture(future, kvManager.client.getScheduler()));

            });

        }


        public TransactPut putIfAbsent() {
            this.ifAbsent = true;

            txnBuilder.addCompare(Rpc.Compare.newBuilder().setTarget(Rpc.Compare.CompareTarget.CREATE).
                    setKey(ByteString.copyFromUtf8(put.getKey())).setCreateRevision(0).setResult(Rpc.Compare.CompareResult.EQUAL)
                    .build());

            return this;
        }


        public TransactPut putIfSameVersion(long version) {
            this.ifSameVersion = true;

            txnBuilder.addCompare(Rpc.Compare.newBuilder().setTarget(Rpc.Compare.CompareTarget.VERSION).
                    setKey(ByteString.copyFromUtf8(put.getKey())).setVersion(version).setResult(Rpc.Compare.CompareResult.EQUAL)
                    .build());

            return this;
        }
    }

    public static class GetRangeResponse {

        public GetRangeResponse(Rpc.RangeResponse response) {
            this.response = response;
        }

        public Kv.KeyValue getValue(int index) {
            if(index < response.getCount()) {
                return response.getKvs(index);
            }
            return null;
        }

        private Rpc.RangeResponse response;
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
                setRangeEnd(ByteString.copyFromUtf8(KeyUtils.getPrefixEnd(directory))).build();
    }

    public static Rpc.RangeRequest createRangeRequest(String prefix, Long limit) {
        return Rpc.RangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(prefix)).
                setRangeEnd(ByteString.copyFromUtf8(KeyUtils.getPrefixEnd(prefix))).setLimit(limit).build();
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
        return put(putOption);
    }

    public Mono<Rpc.PutResponse> put(String key, String value) {
        return put(key, ByteString.copyFromUtf8(value));
    }

    public Mono<Rpc.PutResponse> putMessage(String key, Message message) {

        var putOption = new PutOptions();
        putOption.key = key;
        putOption.value = message.toByteString();
        return put(putOption);

    }

    public <T extends Message> Mono<T> getMessage(String key, Class<T> clazz) {
        return get(key).switchIfEmpty(Mono.defer(Mono::empty)).flatMap(r -> {
            return Mono.just(ProtoUtils.fromByteString(r, clazz));
        });
    }



    //use version as increment counter
    public Mono<Long> inc(String key) {
        var putOption = new PutOptions();
        putOption.key = key;
        putOption.value = ByteString.copyFromUtf8("_ignored");
        putOption.prevKV = true;
        var setKey = put(putOption);
        return setKey.flatMap((r) -> {
            return Mono.just(r.getPrevKv().getVersion());
        });
    }




    public Mono<Rpc.DeleteRangeResponse> delete(Rpc.DeleteRangeRequest request) {
        return Mono.fromFuture(FutureUtils.toCompletableFuture(this.stub.deleteRange(request), client.getScheduler()));
    }

    public Mono<Rpc.DeleteRangeResponse> delete(String key) {
        var request = Rpc.DeleteRangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(key)).build();
        return Mono.fromFuture(FutureUtils.toCompletableFuture(this.stub.deleteRange(request), client.getScheduler()));
    }

    public Mono<Rpc.RangeResponse> getPrefix(String prefix) {
        var request = Rpc.RangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(prefix)).
                setRangeEnd(ByteString.copyFromUtf8(KeyUtils.getPrefixEnd(prefix))).build();

        return Mono.fromFuture(FutureUtils.toCompletableFuture(this.stub.range(request), client.getScheduler()));
    }

    public Mono<Rpc.RangeResponse> getPrefix(String prefix, long limit) {

        var request = Rpc.RangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(prefix)).
                setRangeEnd(ByteString.copyFromUtf8(KeyUtils.getPrefixEnd(prefix))).setLimit(limit).build();

        return Mono.fromFuture(FutureUtils.toCompletableFuture(this.stub.range(request), client.getScheduler()));

    }

    public Mono<Long> getPrefixCount(String directory) {

        var request = Rpc.RangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(directory)).
                setRangeEnd(ByteString.copyFromUtf8(KeyUtils.getPrefixEnd(directory))).setCountOnly(true).build();

        return Mono.fromFuture(FutureUtils.toCompletableFuture(this.stub.range(request), client.getScheduler())).flatMap( (r) -> Mono.just(r.getCount()));
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


    public Mono<Rpc.RangeResponse> getRaw(String key) {

        Rpc.RangeRequest request = Rpc.RangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(key)).build();
        return Mono.fromFuture(FutureUtils.toCompletableFuture(this.stub.range(request), client.getScheduler())).flatMap(
                r -> {
                    return Mono.just(r);
                }
        );
    }

    public Mono<Rpc.TxnResponse> transactDelete(Rpc.DeleteRangeRequest request, TransactDelete options) {
        return transactDelete(request,options, null);
    }
    public Mono<Rpc.TxnResponse> transactDelete(Rpc.DeleteRangeRequest request, TransactDelete options, List<Rpc.DeleteRangeRequest> subRequests) {

        log.debug("transact delete:" + request.getKey() + "-" + options.currentCreateRevision + ":" + options.currentVersion);

        var txnBuilder = Rpc.TxnRequest.newBuilder();

        if(options.compareCreateRevision) {
            txnBuilder.addCompare(Rpc.Compare.newBuilder().setTarget(Rpc.Compare.CompareTarget.CREATE).
                    setKey(request.getKey()).setCreateRevision(options.currentCreateRevision).setResult(Rpc.Compare.CompareResult.EQUAL)
                    .build());
        }

        if(options.compareVersion) {
            txnBuilder.addCompare(Rpc.Compare.newBuilder().setTarget(Rpc.Compare.CompareTarget.VERSION).
                    setKey(request.getKey()).setVersion(options.currentVersion).setResult(Rpc.Compare.CompareResult.EQUAL)
                    .build());
        }

        txnBuilder.addSuccess(Rpc.RequestOp.newBuilder().setRequestDeleteRange(request));
        if(subRequests != null) {
            for(Rpc.DeleteRangeRequest subRequest : subRequests) {
                txnBuilder.addSuccess(Rpc.RequestOp.newBuilder().setRequestDeleteRange(subRequest));
            }
        }

        var txnRequest = txnBuilder.build();
        var future = FutureUtils.toCompletableFuture(stub.txn(txnRequest), client.getScheduler());
        return Mono.fromFuture(future);

    }

    public Mono<Rpc.TxnResponse> transactDelete(String key, TransactDelete options) {
        return transactDelete(Rpc.DeleteRangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(key)).build(), options);
    }

    public Mono<Rpc.TxnResponse> transactDelete(String key, TransactDelete options, List<String> subKeys) {

        var requestList = new ArrayList<Rpc.DeleteRangeRequest>();
        for(String subKey : subKeys) {
            requestList.add(Rpc.DeleteRangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(subKey)).build());
        }
        return transactDelete(Rpc.DeleteRangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(key)).build(), options, requestList);
    }



    public Mono<Rpc.TxnResponse> transactPut(TransactPut put) {

        var request = put.buildTxnRequest(this);
        return request.flatMap((r)-> {

            var future = stub.txn(r);//txnBuilder.addSuccess(Rpc.RequestOp.newBuilder().setRequestPut(r)).build());
            return Mono.fromFuture(FutureUtils.toCompletableFuture(future, client.getScheduler()));

        });
    }

    public <T extends Message> Mono<Rpc.TxnResponse> transactModifyAndPut(String key, T defaultMsg, Class<T> clazz, Function<T, T> modifyCB) {
        //var key = options.getKey();
        /*
        PutOptions put = new PutOptions().withKey(key).withMessage(defaultMsg);
        transactPut(new TransactPut(put).putIfAbsent()).flatMap((r) -> {
            if(r.getSucceeded()) {
                return Mono.just(r);
            } else {

                return getRaw(key).flatMap((curV) -> {
                    if(curV.getCount() == 9)
                    T msg = ProtoUtils.fromJson(curV.toStringUtf8(), clazz);
                    if(modifyCB != null) {
                        msg = modifyCB.apply(msg);
                    }


                    PutOptions put = new PutOptions().withKey(key)

                });

            }

        });
        */
        return getRaw(key).flatMap((r) -> {
            if (r.getCount() == 0) {
                PutOptions put = new PutOptions().withKey(key).withMessage(defaultMsg);
                return transactPut(new TransactPut(put).putIfAbsent()).flatMap((s) -> {
                    if (s.getSucceeded()) {
                        return Mono.just(s);
                    } else {
                        //TODO: should retry
                        return transactModifyAndPut(key, defaultMsg, clazz, modifyCB);//Mono.just(s);
                    }
                });
            } else {
                T msg = ProtoUtils.fromJson(r.getKvs(0).getValue().toStringUtf8(), clazz);
                if (modifyCB != null) {
                    msg = modifyCB.apply(msg);
                }
                PutOptions put = new PutOptions().withKey(key).withMessage(msg);
                return transactPut(new TransactPut(put).putIfSameVersion(r.getKvs(0).getVersion())).flatMap((s)-> {

                    if(s.getSucceeded()) {
                        return Mono.just(s);
                    } else {
                        return transactModifyAndPut(key, defaultMsg, clazz, modifyCB);
                        //TODO: retry
                    }
                });
            }
        });

     //       PutOptions put = new PutOptions().withKey(key)


    }
}
