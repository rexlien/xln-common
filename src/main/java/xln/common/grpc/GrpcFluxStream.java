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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Slf4j
public abstract class GrpcFluxStream<V, R> implements StreamObserver<R> {


    private Flux<R> streamSink;
    private volatile Mono<StreamObserver<V>> streamSource;// = Mono.empty();
   // private volatile MonoSink<StreamObserver<V>> sourceSink;
   private volatile FluxSink<R> publisher;
   private CompletableFuture<StreamObserver<V>> sinkFuture;// = new CompletableFuture<>();


    public GrpcFluxStream() {

    }

    public Mono<StreamObserver<V>> resetStreamSource() {
       sinkFuture = new CompletableFuture<>();
        return Mono.fromFuture(sinkFuture);

    }


    public  Flux<R> initStreamSink(Supplier<StreamObserver<V>> sourceSupplier) {
        this.streamSink = Flux.<R>create((r) -> {
            log.debug("Stream init");

            GrpcFluxStream.this.publisher = r;
            var observer = sourceSupplier.get();
            if(streamSource == null) {
                streamSource = resetStreamSource();//Mono.just(sourceSupplier.get());
            }
            sinkFuture.complete(observer);

        }).retryBackoff(Integer.MAX_VALUE,
                Duration.ofSeconds(3), Duration.ofSeconds(5));

        this.streamSink.subscribe();
        return this.streamSink;
    }

    public Flux<R> getStreamSink() {
        return this.streamSink;
    }

    public Mono<StreamObserver<V>> getStreamSource() {
        return streamSource;
    }

    @Override
    public void onNext(R value) {

    }

    @Override
    public void onError(Throwable t) {
        try {
            streamSource.block().onCompleted();
        }catch (Exception ex) {

        }
        streamSource = resetStreamSource();//Mono.empty();
        //sourceSink.error(t);
        publisher.error(t);
    }

    @Override
    public void onCompleted() {
        log.debug("onCompleted");
        try {
            streamSource.block().onCompleted();
        }catch (Exception ex) {

        }
        publisher.complete();

    }
}
