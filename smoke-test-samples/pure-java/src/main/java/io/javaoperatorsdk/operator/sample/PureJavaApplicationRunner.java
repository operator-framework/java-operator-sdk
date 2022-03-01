package io.javaoperatorsdk.operator.sample;

import java.util.concurrent.Executors;

import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;

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
