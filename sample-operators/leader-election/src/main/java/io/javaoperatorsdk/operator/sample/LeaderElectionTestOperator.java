package io.javaoperatorsdk.operator.sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfigurationBuilder;

public class LeaderElectionTestOperator {

  private static final Logger log = LoggerFactory.getLogger(LeaderElectionTestOperator.class);

  public static void main(String[] args) {
    System.out.println("Starting...");
    String identity = System.getenv("POD_NAME");
    String namespace = System.getenv("POD_NAMESPACE");

    log.info("Starting operator with identity: {}", identity);

    var client = new KubernetesClientBuilder().withConfig(new ConfigBuilder()
        .withNamespace(namespace)
        .build()).build();

    Operator operator = new Operator(client,
        c -> c.withLeaderElectionConfiguration(new LeaderElectionConfigurationBuilder()
            .withLeaseName("leader-election-test")
            .withLeaseNamespace(namespace)
            .withIdentity(identity)
            .build()));
    operator.register(new LeaderElectionTestReconciler(identity));
    operator.installShutdownHook();
    operator.start();
  }

}
