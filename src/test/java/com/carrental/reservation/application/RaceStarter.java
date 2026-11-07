package com.carrental.reservation.application;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

final class RaceStarter {

    private RaceStarter() {
    }

    static <T> List<T> race(int callerCount, Callable<T> call) {
        CountDownLatch startSignal = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(callerCount)) {
            List<Future<T>> futures = IntStream.range(0, callerCount)
                    .mapToObj(_ -> executor.submit(() -> {
                        startSignal.await();
                        return call.call();
                    }))
                    .toList();
            startSignal.countDown();
            return futures.stream().map(RaceStarter::awaitResult).toList();
        }
    }

    private static <T> T awaitResult(Future<T> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new IllegalStateException("Racing call failed unexpectedly", exception);
        }
    }
}
