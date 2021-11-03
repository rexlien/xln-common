package xln.common.etcd;

import com.google.protobuf.ByteString;
import etcdserverpb.Rpc;
import etcdserverpb.WatchGrpc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import reactor.core.Disposable;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import xln.common.dist.KeyUtils;
import xln.common.grpc.GrpcFluxStream;
import xln.common.service.EtcdClient;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Slf4j
public class WatchManager {

    final WatchGrpc.WatchStub stub;
    final EtcdClient client;


    public static class WatchOptions {

        public enum RewatchEvent {
            RE_BEFORE_REWATCH,
            RE_AFTER_REWATCH
        }

        public WatchOptions(String key) {
            this.key = key;
        }

        public WatchOptions withKeyEnd(String keyEnd) {
            this.keyEnd = keyEnd;
            return this;
        }

        public WatchOptions prefixEnd() {
            this.keyEnd = KeyUtils.getPrefixEnd(this.key);
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

        public BiConsumer<RewatchEvent, Long> getReconnectCB() {
            return reconnectCB;
        }

        public WatchOptions setReconnectCB(BiConsumer<RewatchEvent, Long> reconnectCB) {
            this.reconnectCB = reconnectCB;
            return this;
        }

        public Consumer<Long> getDisconnectCB() {
            return disconnectCB;
        }

        public WatchOptions setDisconnectCB(Consumer<Long> disconnectCB) {
            this.disconnectCB = disconnectCB;
            return this;
        }

        private BiConsumer<RewatchEvent, Long> reconnectCB;
        private Consumer<Long> disconnectCB;

    }

    private AtomicLong nextWatchID = new AtomicLong(1);
    public AtomicLong getNextWatchID() {
        return nextWatchID;
    }

    private final GrpcFluxStream<Rpc.WatchRequest, Rpc.WatchResponse> watchStream;

    private final EmitterProcessor<Rpc.WatchResponse> eventProcessor;
    private final Flux<Rpc.WatchResponse> eventSource;
    private final FluxSink<Rpc.WatchResponse> sink;
    private final ExecutorService serializeExecutor = Executors.newFixedThreadPool(1, new CustomizableThreadFactory("xln-watchManager"));
/*
    public static class WatchCommand {
        
        private Consumer<Long> reconnectCB;
        private Consumer<Long> disconnectCB;
        private WatchOptions watchOptions;

        public WatchCommand(WatchOptions watchOptions, Consumer<Long> reconnectCB, Consumer<Long> disconnectCB) {
            this.reconnectCB = reconnectCB;
            this.disconnectCB = disconnectCB;
            this.watchOptions = watchOptions;
        }
    }

 */
    private final ConcurrentHashMap<Long, WatchOptions> watchCommands = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Disposable> subscribers = new ConcurrentHashMap<>();

    public WatchManager(EtcdClient client) {

        this.client = client;
        this.stub = WatchGrpc.newStub(client.getChannel());

        //CompletableFuture<FluxSink<Rpc.WatchResponse>> sinkFuture = new CompletableFuture<>();
        /*
        this.eventSource = Flux.<Rpc.WatchResponse>create((r) -> {
            sinkFuture.complete(r);
        }).publish().autoConnect(0);
*/



        //this.sink = sinkFuture.get();
        eventProcessor = EmitterProcessor.create();
        sink = eventProcessor.sink();
        eventSource = eventProcessor.map((r) -> {
            return r;
        });

        this.watchStream = new GrpcFluxStream<>(client.getChannel(), "watchStream", true) {

            @Override
            public void onNext(Rpc.WatchResponse value) {
                sink.next(value);
            }

            @Override
            public void onReconnected() {
                super.onReconnected();
                //sink.next(Rpc.WatchResponse.getDefaultInstance());
            }

            @Override
            public void onError(Throwable t) {
                log.error("watch stream error");

                super.onError(t);

            }


            @Override
            public void onCompleted() {
                super.onCompleted();
            }
        };

        //register
        this.watchStream.getConnectionEventSource().subscribe((r) ->{

            if (r == GrpcFluxStream.ConnectionEvent.RE_CONNECTED) {
                log.info("Watch manager reconnected");
                serializeExecutor.submit(() ->{
                    watchCommands.forEach((k, v) -> {

                        if(v.reconnectCB != null) {
                            v.reconnectCB.accept(WatchOptions.RewatchEvent.RE_BEFORE_REWATCH, v.watchID);
                        }
                        //clare revision to start rewatch just from now.
                        //NOTE: is it useful to watch from received revision just before disconnected?
                        v.withStartRevision(0L);
                        this.startWatch(v).block(Duration.ofMillis(3000));

                        log.debug("rewatch started");
                        if(v.reconnectCB != null) {
                            v.reconnectCB.accept(WatchOptions.RewatchEvent.RE_AFTER_REWATCH, v.watchID);
                        }
                    });
                });

                //this.startWatch()

            } else if(r == GrpcFluxStream.ConnectionEvent.DISCONNECTED) {
                log.info("Watch manager disconnected");
                serializeExecutor.submit(() -> {
                    watchCommands.forEach((k, v) -> {
                        if(v.disconnectCB != null) {
                            v.disconnectCB.accept(v.watchID);
                        }
                    });
                });
            }

        });
        this.watchStream.initStreamSink(() -> {
            return this.stub.watch(watchStream);
        }).connect();


    }

    public void shutdown() {
        watchStream.getStreamSource().block().onCompleted();
        watchStream.onCompleted();
        watchCommands.clear();
        subscribers.forEach((k, v) -> {
            v.dispose();
        });
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
            watchID = nextWatchID.getAndIncrement();
            options.withWatchID(watchID);
        }
        final long myWatchID = watchID;

        return this.watchStream.getStreamSource().flatMap((s)-> {
            s.onNext(Rpc.WatchRequest.newBuilder().setCreateRequest(createWatchRequest(options)).build());
            return Mono.just(myWatchID);
        }).timeout(Duration.ofMillis(this.client.getTimeoutMillis()));

    }

    public Mono<Long> safeStartWatch(WatchOptions options) {
        return startWatch(options).flatMap(r -> {
            //reconnectCB.accept(r);
            watchCommands.put(r, options);
            return Mono.just(r);
        });

    }



    public Mono<Boolean> stopWatch(long watchID)  {
        return this.watchStream.getStreamSource().flatMap((s)-> {
            s.onNext(Rpc.WatchRequest.newBuilder().setCancelRequest(Rpc.WatchCancelRequest.newBuilder().setWatchId(watchID).build()).build());
            return Mono.just(true);
        }).timeout(Duration.ofMillis(this.client.getTimeoutMillis()));
    }

    public Mono<Boolean> safeStopWatch(long watchID) {
        watchCommands.remove(watchID);
        return stopWatch(watchID);
    }


    public Flux<Rpc.WatchResponse> getEventSource() {
        return eventSource;
    }

    public Flux<GrpcFluxStream.ConnectionEvent> getConnectionEventSource() {
        return watchStream.getConnectionEventSource();
    }

    public void subscribeEventSource(long watchId, Consumer<Rpc.WatchResponse> cb) {

        var subscriber = this.eventSource.doOnEach(r -> {
            if(r.get().getWatchId() == watchId) {
                cb.accept(r.get());
            }
        }).subscribe();
        subscribers.put(watchId, subscriber);

    }

    public void deSubscribeEventSource(long watchId) {

        var subscriber = subscribers.get(watchId);
        if(subscriber != null) {
            subscriber.dispose();
        }
        subscribers.remove(watchId);
    }



}
