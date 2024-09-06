package io.javaoperatorsdk.operator.sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;

public class ControllerNamespaceDeletionOperator {

  private static final Logger log = LoggerFactory.getLogger(ControllerNamespaceDeletionOperator.class);

  public static void main(String[] args) {
    Operator operator = new Operator();

    operator.register(new ControllerNamespaceDeletionReconciler());
    operator.start();
  }
}
