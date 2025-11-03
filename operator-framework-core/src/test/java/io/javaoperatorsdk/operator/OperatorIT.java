package io.javaoperatorsdk.operator;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("rawtypes")
class OperatorIT {

  @Test
  void shouldBePossibleToRetrieveNumberOfRegisteredControllers() {
    final var operator = new Operator();
    assertEquals(0, operator.getRegisteredControllersNumber());

    operator.register(new FooReconciler());
    assertEquals(1, operator.getRegisteredControllersNumber());
  }

  @Test
  void shouldBePossibleToRetrieveRegisteredControllerByName() {
    final var operator = new Operator();
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
  void shouldThrowExceptionIf() {
    final var operator = new OperatorExtension();
    assertNotNull(operator);
    operator.setConfigurationService(ConfigurationService.newOverriddenConfigurationService(null));
    assertNotNull(operator.getConfigurationService());

    // should fail because the implementation is not providing a valid configuration service when
    // constructing the operator
    assertThrows(
        IllegalStateException.class,
        () -> new OperatorExtension(MockKubernetesClient.client(ConfigMap.class)));
  }

  private static class FooReconciler implements Reconciler<ConfigMap> {
    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context context) {
      return UpdateControl.noUpdate();
    }
  }

  private static class OperatorExtension extends Operator {
    public OperatorExtension() {}

    public OperatorExtension(KubernetesClient client) {
      super(client);
    }

    /**
     * Overridden to mimic deferred initialization (or rather the fact that we don't want to do that
     * processing at this time so return null).
     */
    @Override
    protected ConfigurationService initConfigurationService(
        KubernetesClient client, Consumer<ConfigurationServiceOverrider> overrider) {
      return null;
    }
  }
}
