package xln.common.utils;

import lombok.val;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

public final class ExecutorUtils {

    private static class MyForkJoinWokerThread extends ForkJoinWorkerThread {
        public MyForkJoinWokerThread(ForkJoinPool pool) {
            super(pool);
        }
    }

    private static class MyJoinWorkerThreadFactory implements ForkJoinPool.ForkJoinWorkerThreadFactory {
        @Override
        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            MyForkJoinWokerThread newThread = new MyForkJoinWokerThread(pool);
            newThread.setName("XLN-ForkJoinThread");
            return newThread;
        }
    }

    private static final ForkJoinPool forkJoinExecutor = new ForkJoinPool(Runtime.getRuntime().availableProcessors(),
            new MyJoinWorkerThreadFactory(),
            null, true);

    public static ForkJoinPool getForkJoinExecutor() {
        return forkJoinExecutor;
    }

}
