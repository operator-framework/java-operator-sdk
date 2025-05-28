package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ConfigurationServiceOverriderTest {

  private static final Metrics METRICS = new Metrics() {};

  private static final LeaderElectionConfiguration LEADER_ELECTION_CONFIGURATION =
      new LeaderElectionConfiguration("foo", "fooNS");

  private static final Cloner CLONER =
      new Cloner() {
        @Override
        public <R extends HasMetadata> R clone(R object) {
          return null;
        }
      };

  final BaseConfigurationService config =
      new BaseConfigurationService(null) {
        @Override
        public boolean checkCRDAndValidateLocalModel() {
          return false;
        }

        @Override
        public Metrics getMetrics() {
          return METRICS;
        }

        @Override
        public Cloner getResourceCloner() {
          return CLONER;
        }

        @Override
        public Optional<LeaderElectionConfiguration> getLeaderElectionConfiguration() {
          return Optional.of(LEADER_ELECTION_CONFIGURATION);
        }
      };

  @Test
  void overrideShouldWork() {

    final var overridden =
        new ConfigurationServiceOverrider(config)
            .checkingCRDAndValidateLocalModel(true)
            .withExecutorService(Executors.newSingleThreadExecutor())
            .withWorkflowExecutorService(Executors.newFixedThreadPool(4))
            .withCloseClientOnStop(false)
            .withResourceCloner(
                new Cloner() {
                  @Override
                  public <R extends HasMetadata> R clone(R object) {
                    return null;
                  }
                })
            .withConcurrentReconciliationThreads(25)
            .withMetrics(new Metrics() {})
            .withLeaderElectionConfiguration(
                new LeaderElectionConfiguration("newLease", "newLeaseNS"))
            .withInformerStoppedHandler((informer, ex) -> {})
            .withReconciliationTerminationTimeout(Duration.ofSeconds(30))
            .build();

    assertNotEquals(config.closeClientOnStop(), overridden.closeClientOnStop());
    assertNotEquals(
        config.checkCRDAndValidateLocalModel(), overridden.checkCRDAndValidateLocalModel());
    assertNotEquals(
        config.concurrentReconciliationThreads(), overridden.concurrentReconciliationThreads());
    assertNotEquals(config.getExecutorService(), overridden.getExecutorService());
    assertNotEquals(config.getWorkflowExecutorService(), overridden.getWorkflowExecutorService());
    assertNotEquals(config.getMetrics(), overridden.getMetrics());
    assertNotEquals(
        config.getLeaderElectionConfiguration(), overridden.getLeaderElectionConfiguration());
    assertNotEquals(
        config.getInformerStoppedHandler(), overridden.getLeaderElectionConfiguration());
    assertNotEquals(
        config.reconciliationTerminationTimeout(), overridden.reconciliationTerminationTimeout());
  }

  @Test
  void threadCountConfiguredProperly() {
    final var overridden =
        new ConfigurationServiceOverrider(config)
            .withConcurrentReconciliationThreads(13)
            .withConcurrentWorkflowExecutorThreads(14)
            .build();
    assertThat(((ThreadPoolExecutor) overridden.getExecutorService()).getMaximumPoolSize())
        .isEqualTo(13);
    assertThat(((ThreadPoolExecutor) overridden.getWorkflowExecutorService()).getMaximumPoolSize())
        .isEqualTo(14);
  }
}
