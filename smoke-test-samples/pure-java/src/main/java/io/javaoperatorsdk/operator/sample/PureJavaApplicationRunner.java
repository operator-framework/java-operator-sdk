package io.javaoperatorsdk.operator.sample;

import java.util.concurrent.Executors;

import io.javaoperatorsdk.operator.Operator;

public class PureJavaApplicationRunner {

  public static void main(String[] args) {
    Operator operator =
        new Operator(overrider -> overrider.withExecutorService(Executors.newCachedThreadPool())
            .withConcurrentReconciliationThreads(2));
    operator.register(new CustomServiceReconciler());
    operator.start();
  }
}
