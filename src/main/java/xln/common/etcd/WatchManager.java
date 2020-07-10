package xln.common.etcd;

import com.google.protobuf.ByteString;
import etcdserverpb.Rpc;
import etcdserverpb.WatchGrpc;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.*;
import xln.common.grpc.GrpcFluxStream;
import xln.common.service.EtcdClient;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class WatchManager {

    final WatchGrpc.WatchStub stub;
    final EtcdClient client;

    AtomicLong nextWatchID = new AtomicLong(1);


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

    private final GrpcFluxStream<Rpc.WatchRequest, Rpc.WatchResponse> watchStream;

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
        this.watchStream = new GrpcFluxStream<>() {

            @Override
            public void onNext(Rpc.WatchResponse value) {
                sink.next(value);
            }
            @Override
            public void onError(Throwable t) {
                super.onError(t);
                //sink.error(t);
            }

            @Override
            public void onCompleted() {
                super.onCompleted();
                //sink.complete();
            }
        };
        this.watchStream.initStreamSink(() -> {
            return this.stub.watch(watchStream);
        });


    }

    public void shutdown() {
        watchStream.getStreamSource().block().onCompleted();
        watchStream.onCompleted();
        sink.complete();

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


    public Mono<Long> startWatch(WatchOptions options)  {

        long watchID = options.getWatchID();
        if(options.getWatchID() == 0) {
            watchID = curWatchID.getAndIncrement();
            options.withWatchID(watchID);
        }
        final long myWatchID = watchID;

        return this.watchStream.getStreamSource().flatMap((s)-> {
            s.onNext(Rpc.WatchRequest.newBuilder().setCreateRequest(createWatchRequest(options)).build());
            return Mono.just(myWatchID);
        }).timeout(Duration.ofMillis(this.client.getTimeoutMillis()));

    }

    public Mono<Boolean> stopWatch(long watchID)  {
        return this.watchStream.getStreamSource().flatMap((s)-> {
            s.onNext(Rpc.WatchRequest.newBuilder().setCancelRequest(Rpc.WatchCancelRequest.newBuilder().setWatchId(watchID).build()).build());
            return Mono.just(true);
        }).timeout(Duration.ofMillis(this.client.getTimeoutMillis()));
    }



    public Flux<Rpc.WatchResponse> getEventSource() {
        return eventSource;
    }



}
