package xln.common.grpc;

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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Slf4j
public abstract class GrpcFluxStream<V, R> implements StreamObserver<R> {


    private Flux<R> streamSink;
    private volatile Mono<StreamObserver<V>> reqStream;// = Mono.empty();
   // private volatile MonoSink<StreamObserver<V>> sourceSink;
    private volatile FluxSink<R> publisher;
    private CompletableFuture<StreamObserver<V>> sinkFuture;// = new CompletableFuture<>();
    private String name = "default";
    private boolean enableRetry = true;

    public GrpcFluxStream() {

    }
    public GrpcFluxStream(String name, boolean enableRetry ) {
        this.name = name;
        this.enableRetry = enableRetry;
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
            if(reqStream == null) {
                reqStream = resetRequestStream();//Mono.just(sourceSupplier.get());
            }
            sinkFuture.complete(observer);

        });
        if(enableRetry) {
            streamSink = streamSink.retryWhen(Retry.fixedDelay(Integer.MAX_VALUE,
                    Duration.ofSeconds(5)));
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

    @Override
    public void onError(Throwable t) {
        try {
            reqStream.block().onCompleted();
        }catch (Exception ex) {

        }
        reqStream = resetRequestStream();//Mono.empty();
        log.debug(t.getMessage());
        publisher.error(t);
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
