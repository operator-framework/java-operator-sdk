package io.javaoperatorsdk.operator.sample;

import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;

public class PureJavaApplicationRunner {

  public static void main(String[] args) {
    Operator operator =
        new Operator(ConfigurationServiceOverrider.override(DefaultConfigurationService.instance())
            .withConcurrentReconciliationThreads(2)
            .build());
    operator.registerController(new CustomServiceController());
    operator.start();
  }
}
