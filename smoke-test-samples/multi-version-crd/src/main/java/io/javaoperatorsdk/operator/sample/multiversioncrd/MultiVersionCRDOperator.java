package io.javaoperatorsdk.operator.sample.multiversioncrd;

import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;

public class MultiVersionCRDOperator {

  public static void main(String[] args) {
    Operator operator =
        new Operator(
            ConfigurationServiceOverrider.override(DefaultConfigurationService.instance()).build());
    operator.register(new MultiVersionCRDTestReconciler1());
    operator.register(new MultiVersionCRDTestReconciler2());
    operator.start();
  }
}
