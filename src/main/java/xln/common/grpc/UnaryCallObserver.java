package xln.common.grpc;

import io.grpc.stub.StreamObserver;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

public class UnaryCallObserver<R> implements StreamObserver<R> {


    private volatile MonoSink<R> sinker = null;
    private Mono<R> publisher = Mono.create((r)->{
        sinker = r;
    });

    public UnaryCallObserver() {

    }

    public Mono<R> getPublisher() {
        return publisher;
    }

    @Override
    public void onNext(R value) {
        sinker.success(value);
    }

    @Override
    public void onError(Throwable t) {
        sinker.error(t);
    }

    @Override
    public void onCompleted() {
        sinker.success();
    }
}
