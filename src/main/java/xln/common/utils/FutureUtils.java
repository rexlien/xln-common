package xln.common.utils;


import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FutureUtils {

    public static <T> CompletableFuture<T> toCompletableFuture(ListenableFuture<T> listenableFuture, Executor executor) {

        CompletableFuture<T> ret = new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                super.cancel(mayInterruptIfRunning);
                return listenableFuture.cancel(mayInterruptIfRunning);
            }
        };
        Futures.addCallback(
                listenableFuture,
                new FutureCallback<T>() {
                    public void onSuccess(T result) {
                        ret.complete(result);
                    }
                    public void onFailure(Throwable thrown) {
                        ret.completeExceptionally(thrown);
                    }
                }, executor);

        return ret;
    }
}
