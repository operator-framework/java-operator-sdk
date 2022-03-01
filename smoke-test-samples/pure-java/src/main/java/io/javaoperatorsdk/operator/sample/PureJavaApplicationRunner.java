package io.javaoperatorsdk.operator.sample;

import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PureJavaApplicationRunner {

  public static void main(String[] args) {
    Operator operator =
        new Operator(
            ConfigurationServiceOverrider.override(DefaultConfigurationService.instance())
                .withExecutorService(Executors.newCachedThreadPool())
                .withConcurrentReconciliationThreads(2)
                .build());
    operator.register(new CustomServiceReconciler());
    operator.start();
  }
}
