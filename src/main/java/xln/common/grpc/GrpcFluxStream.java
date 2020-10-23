package xln.common.grpc;

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.Disposable;
import reactor.core.publisher.*;
import reactor.util.retry.Retry;
import xln.common.proto.command.Command;
import xln.common.utils.FluxSinkPair;
import xln.common.utils.FluxUtils;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Slf4j
public abstract class GrpcFluxStream<V, R> implements StreamObserver<R> {

    public enum ConnectionEvent {
        INIT_CONNECTED,
        RE_CONNECTED,
        DISCONNECTED,
    }

    private enum ConnectionState {
        CONNECTED,
        DISCONNECTED
    }

    private Flux<R> streamSink;
    private volatile Mono<StreamObserver<V>> reqStream;
    private volatile FluxSink<R> publisher;
    private volatile CompletableFuture<StreamObserver<V>> sinkFuture;
    private final String name;
    private final boolean enableRetry;
    private final ManagedChannel managedChannel;
    private final FluxSinkPair<ConnectionEvent> connectionEventFlux;
    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;

    public GrpcFluxStream(ManagedChannel channel, String name, boolean enableRetry ) {
        this.name = name;
        this.enableRetry = enableRetry;
        this.managedChannel = channel;
        connectionEventFlux = FluxUtils.INSTANCE.createFluxSinkPair();
    }


    private Mono<StreamObserver<V>> resetRequestStream() {
       sinkFuture = new CompletableFuture<>();
        return Mono.fromFuture(sinkFuture);

    }

    public  Flux<R> initStreamSink(Supplier<StreamObserver<V>> sourceSupplier) {


        this.streamSink = Flux.create((r) -> {

            //NOTE: in non-grpc thread when reconnected
            log.debug("Stream init:" + name);

            GrpcFluxStream.this.publisher = r;
            var observer = sourceSupplier.get();

            //first time
            if(reqStream == null) {
                reqStream = resetRequestStream();
            } else {
                onReconnected();
            }
            sinkFuture.complete(observer);
            if(this.managedChannel.getState(true) == ConnectivityState.READY) {
                connectionState = ConnectionState.CONNECTED;
            }

        });
        if(enableRetry) {
            streamSink = streamSink.retryWhen(Retry.fixedDelay(Integer.MAX_VALUE,
                    Duration.ofSeconds(1)));
        }
        this.streamSink.subscribe();
        return this.streamSink;
    }

    public Flux<R> getStreamSink() {
        return this.streamSink;
    }

    public Mono<StreamObserver<V>> getStreamSource() {
        return reqStream;
    }

    @Override
    public void onNext(R value) {
        publisher.next(value);
    }

    public void onReconnected() {

        connectionEventFlux.getSink().next(ConnectionEvent.RE_CONNECTED);
    }

    public Flux<ConnectionEvent> getConnectionEventSource() {
        return connectionEventFlux.getFlux();
    }


    private void notifyConnected() {
        managedChannel.notifyWhenStateChanged(ConnectivityState.CONNECTING ,  () -> {

            if (this.managedChannel.getState(true) == ConnectivityState.READY) {
                log.info("Reconnected");
                if(enableRetry) {
                    publisher.error(new Throwable());
                }
            } else {
                try {
                    Thread.sleep(5000);
                }catch (Exception ex) {

                }
                notifyConnected();
            }
        });
    }
    @Override
    public void onError(Throwable t) {

        connectionState = ConnectionState.DISCONNECTED;
        connectionEventFlux.getSink().next(ConnectionEvent.DISCONNECTED);
        try {
            reqStream.block().onCompleted();
        }catch (Exception ex) {
        }
        reqStream = resetRequestStream();

        if(managedChannel != null) {
            notifyConnected();
        }



    }

    @Override
    public void onCompleted() {
        log.debug("onCompleted");
        try {
            if (connectionState == ConnectionState.CONNECTED) {
                reqStream.block(Duration.ofMillis(1000)).onCompleted();
            }
        }catch (Exception ex) {

        }
        publisher.complete();

    }
}
