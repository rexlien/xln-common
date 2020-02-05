package xln.common.etcd;

import com.google.protobuf.ByteString;
import etcdserverpb.Rpc;
import etcdserverpb.WatchGrpc;
import io.grpc.stub.StreamObserver;
import kotlin.Pair;
import lombok.extern.slf4j.Slf4j;
import mvccpb.Kv;
import reactor.core.CoreSubscriber;
import reactor.core.Disposable;
import reactor.core.publisher.*;
import xln.common.service.EtcdClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Slf4j
public class WatchManager {

    private final WatchGrpc.WatchStub stub;
    private final EtcdClient client;

    public static class WatchEvent {


    }

    public static class WatchOptions {

        public WatchOptions(String key) {
            this.key = key;
        }

        public WatchOptions withKeyEnd(String keyEnd) {
            this.keyEnd = keyEnd;
            return this;
        }

        public String getKey() {
            return key;
        }

        public String getKeyEnd() {
            return keyEnd;
        }


        public WatchOptions withPrevKV(boolean prevKV) {
            this.prevKV = prevKV;
            return this;
        }

        public boolean isPrevKV() {
            return prevKV;
        }

        private boolean prevKV;
        private final String key;
        private String keyEnd;

        public WatchOptions withNoPut(boolean noPut) {
            this.noPut = noPut;
            return this;
        }

        public WatchOptions withNoDelete(boolean noDelete) {
            this.noDelete = noDelete;
            return this;
        }

        public boolean isNoPut() {
            return noPut;
        }

        public boolean isNoDelete() {
            return noDelete;
        }

        private boolean noPut = false;
        private  boolean noDelete = false;

        public long getStartRevision() {
            return startRevision;
        }

        public WatchOptions withStartRevision(long revision) {
            this.startRevision = revision;
            return this;
        }

        private long startRevision = 0;

        public long getWatchID() {
            return watchID;
        }

        public WatchOptions withWatchID(long watchID) {
            this.watchID = watchID;
            return this;
        }

        private long watchID = 0;

    }

    private AtomicLong curWatchID = new AtomicLong(1);

    private final StreamObserver<Rpc.WatchRequest> watchRequest;
    private final StreamObserver<Rpc.WatchResponse> watchResponse;

    private final Flux<Rpc.WatchResponse> eventSource;
    private final FluxSink<Rpc.WatchResponse> sink;


    public WatchManager(EtcdClient client) throws Exception {

        this.client = client;
        this.stub = WatchGrpc.newStub(client.getChannel());

        CompletableFuture<FluxSink<Rpc.WatchResponse>> sinkFuture = new CompletableFuture<>();
        this.eventSource = Flux.<Rpc.WatchResponse>create((r) -> {
            sinkFuture.complete(r);
        }).publish().autoConnect(0);



        this.sink = sinkFuture.get();
        this.watchResponse = new StreamObserver<>() {

            @Override
            public void onNext(Rpc.WatchResponse value) {
                sink.next(value);
            }
            @Override
            public void onError(Throwable t) {
                sink.error(t);
            }

            @Override
            public void onCompleted() {
                sink.complete();
            }
        };
        this.watchRequest = this.stub.watch(watchResponse);

    }

    public void shutdown() {
        watchRequest.onCompleted();
        watchResponse.onCompleted();

    }


    public Rpc.WatchCreateRequest createWatchRequest(WatchOptions options) {

        var builder = Rpc.WatchCreateRequest.newBuilder().setKey(ByteString.copyFromUtf8(options.key));
        if(options.getKeyEnd() != null) {
            builder.setRangeEnd(ByteString.copyFromUtf8(options.getKeyEnd()));
        }
        if(options.isPrevKV()) {
            builder.setPrevKv(options.isPrevKV());
        }
       if(options.isNoPut()) {
            builder.addFilters(Rpc.WatchCreateRequest.FilterType.NOPUT);
       }
       if(options.isNoDelete()) {
           builder.addFilters(Rpc.WatchCreateRequest.FilterType.NODELETE);
       }

       builder.setStartRevision(options.getStartRevision());
       if(options.getWatchID() != 0) {
           builder.setWatchId(options.getWatchID());
       }

       return builder.build();

    }


    public long startWatch(WatchOptions options) {

        long watchID = options.getWatchID();
        if(options.getWatchID() == 0) {
            watchID = curWatchID.getAndIncrement();
            options.withWatchID(watchID);
        }
        watchRequest.onNext(Rpc.WatchRequest.newBuilder().setCreateRequest(createWatchRequest(options)).build());
        return watchID;
    }

    public void stopWatch(long watchID) {
        watchRequest.onNext(Rpc.WatchRequest.newBuilder().setCancelRequest(Rpc.WatchCancelRequest.newBuilder().setWatchId(watchID).build()).build());
    }

    public Flux<Rpc.WatchResponse> getEventSource() {
        return eventSource;
    }



}