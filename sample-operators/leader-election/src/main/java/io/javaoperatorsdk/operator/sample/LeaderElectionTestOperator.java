package io.javaoperatorsdk.operator.sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;

public class LeaderElectionTestOperator {

  private static final Logger log = LoggerFactory.getLogger(LeaderElectionTestOperator.class);

  public static void main(String[] args) {
    String identity = System.getenv("POD_NAME");
    String namespace = System.getenv("POD_NAMESPACE");

    log.info("Starting operator with identity: {}", identity);

    var client = new KubernetesClientBuilder().build();
    Operator operator = new Operator(client,
        c -> c.withLeaderElectionConfiguration(
            new LeaderElectionConfiguration("leader-election-test", namespace, identity)));

    operator.register(new LeaderElectionTestReconciler(identity));
    operator.installShutdownHook();
    operator.start();
  }

}
