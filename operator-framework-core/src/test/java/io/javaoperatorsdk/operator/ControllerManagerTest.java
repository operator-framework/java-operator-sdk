package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ResolvedControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.sample.simple.DuplicateCRController;
import io.javaoperatorsdk.operator.sample.simple.TestCustomReconciler;
import io.javaoperatorsdk.operator.sample.simple.TestCustomReconcilerOtherV1;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResourceOtherV1;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerManagerTest {

  @Test
  void shouldNotAddMultipleControllersForSameCustomResource() {
    final var registered = new TestControllerConfiguration<>(new TestCustomReconciler(null),
        TestCustomResource.class);
    final var duplicated =
        new TestControllerConfiguration<>(new DuplicateCRController(), TestCustomResource.class);

    checkException(registered, duplicated);
  }

  @Test
  void addingMultipleControllersForCustomResourcesWithSameVersionsShouldNotWork() {
    final var registered = new TestControllerConfiguration<>(new TestCustomReconciler(null),
        TestCustomResource.class);
    final var duplicated = new TestControllerConfiguration<>(new TestCustomReconcilerOtherV1(),
        TestCustomResourceOtherV1.class);

    checkException(registered, duplicated);
  }

  private <T extends HasMetadata, U extends HasMetadata> void checkException(
      TestControllerConfiguration<T> registered,
      TestControllerConfiguration<U> duplicated) {

    ConfigurationService configurationService = new BaseConfigurationService();

    final var exception = assertThrows(OperatorException.class, () -> {
      final var controllerManager =
          new ControllerManager(configurationService.getExecutorServiceManager());
      controllerManager.add(new Controller<>(registered.controller, registered,
          MockKubernetesClient.client(registered.getResourceClass())));
      controllerManager.add(new Controller<>(duplicated.controller, duplicated,
          MockKubernetesClient.client(duplicated.getResourceClass())));
    });
    final var msg = exception.getMessage();
    assertTrue(
        msg.contains("Cannot register controller '" + duplicated.getName() + "'")
            && msg.contains(registered.getName())
            && msg.contains(registered.getResourceTypeName()));
  }

  private static class TestControllerConfiguration<R extends HasMetadata>
      extends ResolvedControllerConfiguration<R> {
    private final Reconciler<R> controller;

    public TestControllerConfiguration(Reconciler<R> controller, Class<R> crClass) {
      super(crClass, getControllerName(controller), controller.getClass(),
          new BaseConfigurationService());
      this.controller = controller;
    }

    static <R extends HasMetadata> String getControllerName(
        Reconciler<R> controller) {
      return controller.getClass().getSimpleName() + "Controller";
    }
  }
}
