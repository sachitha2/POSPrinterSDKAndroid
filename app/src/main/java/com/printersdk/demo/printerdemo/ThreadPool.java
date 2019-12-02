package com.printersdk.demo.printerdemo;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPool {

    private static ThreadPool threadPool;

    private ThreadPoolExecutor threadPoolExecutor;


    private final static int MAX_POOL_COUNTS = 20;


    private final static long ALIVETIME = 200L;


    private final static int CORE_POOL_SIZE = 20;


    private BlockingQueue<Runnable> mWorkQueue = new ArrayBlockingQueue<>(CORE_POOL_SIZE);

    private ThreadFactory threadFactory = new ThreadFactoryBuilder("ThreadPool");

    private ThreadPool() {

        threadPoolExecutor = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_COUNTS, ALIVETIME, TimeUnit.SECONDS, mWorkQueue, threadFactory);
    }

    public static ThreadPool getInstantiation() {
        if (threadPool == null) {
            threadPool = new ThreadPool();
        }
        return threadPool;
    }

    public void addTask(Runnable runnable) {
        if (runnable == null) {
            throw new NullPointerException("addTask(Runnable runnable)传入参数为空");
        }
        if (threadPoolExecutor != null && threadPoolExecutor.getActiveCount() < MAX_POOL_COUNTS) {
            threadPoolExecutor.execute(runnable);
        }
    }

    public void stopThreadPool() {
        if (threadPoolExecutor != null) {
            threadPoolExecutor.shutdown();
            threadPoolExecutor = null;
            threadPool = null;
        }
    }
}
