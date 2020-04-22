package xln.common.grpc;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Supplier;

@Slf4j
public abstract class GrpcFluxStream<V, R> implements StreamObserver<R> {


    private Flux<R> streamSink;
    private volatile Mono<StreamObserver<V>> streamSource = Mono.empty();
    private volatile Subscriber<?> publisher;

    public GrpcFluxStream() {

    }
    public  Flux<R> initStreamSink(Supplier<StreamObserver<V>> sourceSupplier) {
        this.streamSink = Flux.<R>from((r) -> {
            log.debug("Stream init");
            GrpcFluxStream.this.publisher = r;
            streamSource = Mono.just(sourceSupplier.get());

        }).retryBackoff(Integer.MAX_VALUE,
                Duration.ofSeconds(5), Duration.ofSeconds(10));

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
        publisher.onError(t);
        try {
            streamSource.block().onCompleted();
        }catch (Exception ex) {

        }
        streamSource = Mono.empty();

    }

    @Override
    public void onCompleted() {
        log.debug("onCompleted");
        try {
            streamSource.block().onCompleted();
        }catch (Exception ex) {

        }
        publisher.onComplete();

    }
}
