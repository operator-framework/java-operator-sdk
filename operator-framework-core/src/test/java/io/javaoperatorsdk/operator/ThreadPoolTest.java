package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.*;

public class ThreadPoolTest {

    public static final int NUM = 20000;

    @Test
    void test() {
//        ThreadPoolExecutor es = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        var es = new ThreadPoolExecutor(10, 50, 1, TimeUnit.MINUTES,
                new LinkedBlockingDeque<>(300));
        var sumSubmit = 0;
        var futures = new ArrayList<Future<Void>>();
        for (int i = 0; i< NUM; i++) {
            var now = System.nanoTime() ;
            Future f = es.submit(() -> {
                try {
                    Thread.sleep(200);
                    System.out.println("Finished " + Thread.currentThread());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            var duration = System.nanoTime() - now;
            sumSubmit += duration;
            System.out.println("submit duration in nanos: "+duration);
            futures.add(f);
        }

        futures.forEach(f-> {
            try {
                f.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        System.out.println("finished:"+es.getPoolSize());
        System.out.println("avg submit:"+sumSubmit / NUM);
    }


}
