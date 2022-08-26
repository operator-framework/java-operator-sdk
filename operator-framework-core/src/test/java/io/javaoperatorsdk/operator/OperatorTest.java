package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.AbstractConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("rawtypes")
class OperatorTest {

  private final KubernetesClient kubernetesClient = MockKubernetesClient.client(ConfigMap.class);
  private Operator operator;

  @BeforeAll
  @AfterAll
  static void setUpConfigurationServiceProvider() {
    ConfigurationServiceProvider.reset();
  }

  @BeforeEach
  void initOperator() {
    ConfigurationServiceProvider.reset();
    operator = new Operator(kubernetesClient);
  }

  @Test
  @DisplayName("should throw `OperationException` when Configuration is null")
  public void shouldThrowOperatorExceptionWhenConfigurationIsNull() {
    // use a ConfigurationService that doesn't automatically create configurations
    ConfigurationServiceProvider.reset();
    ConfigurationServiceProvider.set(new AbstractConfigurationService(null));

    Assertions.assertThrows(OperatorException.class, () -> operator.register(new FooReconciler()));
  }

  @Test
  void shouldBePossibleToRetrieveNumberOfRegisteredControllers() {
    assertEquals(0, operator.getRegisteredControllersNumber());

    operator.register(new FooReconciler());
    assertEquals(1, operator.getRegisteredControllersNumber());
  }

  @Test
  void shouldBePossibleToRetrieveRegisteredControllerByName() {
    final var reconciler = new FooReconciler();
    final var name = ReconcilerUtils.getNameFor(reconciler);

    var registeredControllers = operator.getRegisteredControllers();
    assertTrue(operator.getRegisteredController(name).isEmpty());
    assertTrue(registeredControllers.isEmpty());

    operator.register(reconciler);
    final var maybeController = operator.getRegisteredController(name);
    assertTrue(maybeController.isPresent());
    assertEquals(name, maybeController.map(rc -> rc.getConfiguration().getName()).orElseThrow());

    registeredControllers = operator.getRegisteredControllers();
    assertEquals(1, registeredControllers.size());
    assertEquals(maybeController.get(), registeredControllers.stream().findFirst().orElseThrow());
  }

  @Test
  void shouldBeAbleToProvideLeaderElectionConfiguration() {
    assertTrue(ConfigurationServiceProvider.instance().getLeaderElectionConfiguration().isEmpty());
    new Operator(kubernetesClient, c -> c.withLeaderElectionConfiguration(
        new LeaderElectionConfiguration("leader-election-test", "namespace", "identity")));
    assertEquals("identity", ConfigurationServiceProvider.instance()
        .getLeaderElectionConfiguration().orElseThrow().getIdentity().orElseThrow());
  }

  @ControllerConfiguration
  private static class FooReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context context) {
      return UpdateControl.noUpdate();
    }
  }

}
