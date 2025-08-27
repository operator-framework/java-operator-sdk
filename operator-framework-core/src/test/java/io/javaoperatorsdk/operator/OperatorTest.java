package io.javaoperatorsdk.operator;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
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

  @BeforeEach
  void initOperator() {
    operator = new Operator(kubernetesClient);
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
  void overriddenConfigurationShouldBeUpdatedInConfigurationService() {
    final var reconciler = new FooReconciler();
    operator.register(new FooReconciler(), override -> override.settingNamespace("test"));
    final var config = operator.getConfigurationService().getConfigurationFor(reconciler);
    final var namespaces = config.getInformerConfig().getNamespaces();
    assertEquals(Set.of("test"), namespaces);
  }

  @ControllerConfiguration
  private static class FooReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context context) {
      return UpdateControl.noUpdate();
    }
  }
}
