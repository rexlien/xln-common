package xln.common.grpc;

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.util.retry.Retry;
import xln.common.proto.command.Command;
import xln.common.utils.FluxSinkPair;
import xln.common.utils.FluxUtils;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Slf4j
public abstract class GrpcFluxStream<V, R> implements StreamObserver<R> {

    public enum ConnectionEvent {
        INIT_CONNECTED,
        RE_CONNECTED,
        DISCONNECTED,
    }

    private Flux<R> streamSink;
    private volatile Mono<StreamObserver<V>> reqStream;
    private volatile FluxSink<R> publisher;
    private CompletableFuture<StreamObserver<V>> sinkFuture;
    private final String name;
    private final boolean enableRetry;
    private final ManagedChannel managedChannel;
    private final FluxSinkPair<ConnectionEvent> connectionEventFlux;

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

        this.streamSink = Flux.<R>create((r) -> {
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

        connectionEventFlux.getSink().next(ConnectionEvent.DISCONNECTED);
        if(managedChannel != null) {
            notifyConnected();
        }

        try {
            reqStream.block().onCompleted();
        }catch (Exception ex) {

        }
        reqStream = resetRequestStream();//Mono.empty();
        log.debug(t.getMessage());
        //publisher.error(t);
    }

    @Override
    public void onCompleted() {
        log.debug("onCompleted");
        try {
            reqStream.block().onCompleted();
        }catch (Exception ex) {

        }
        publisher.complete();

    }
}
