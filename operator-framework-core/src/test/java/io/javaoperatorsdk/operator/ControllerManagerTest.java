package io.javaoperatorsdk.operator;

import org.junit.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.Operator.ControllerManager;
import io.javaoperatorsdk.operator.api.config.DefaultControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.sample.simple.DuplicateCRController;
import io.javaoperatorsdk.operator.sample.simple.TestCustomReconciler;
import io.javaoperatorsdk.operator.sample.simple.TestCustomReconcilerV2;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResourceV2;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ControllerManagerTest {

  @Test
  public void shouldNotAddMultipleControllersForSameCustomResource() {
    final var registered = new TestControllerConfiguration<>(new TestCustomReconciler(null),
        TestCustomResource.class);
    final var duplicated =
        new TestControllerConfiguration<>(new DuplicateCRController(), TestCustomResource.class);

    checkException(registered, duplicated);
  }

  @Test
  public void addingMultipleControllersForCustomResourcesWithDifferentVersionsShouldNotWork() {
    final var registered = new TestControllerConfiguration<>(new TestCustomReconciler(null),
        TestCustomResource.class);
    final var duplicated = new TestControllerConfiguration<>(new TestCustomReconcilerV2(),
        TestCustomResourceV2.class);

    checkException(registered, duplicated);

  }

  private <T extends HasMetadata, U extends HasMetadata> void checkException(
      TestControllerConfiguration<T> registered,
      TestControllerConfiguration<U> duplicated) {
    final var exception = assertThrows(OperatorException.class, () -> {
      final var controllerManager = new ControllerManager();
      controllerManager.add(new Controller<>(registered.controller, registered, null));
      controllerManager.add(new Controller<>(duplicated.controller, duplicated, null));
    });
    final var msg = exception.getMessage();
    assertTrue(
        msg.contains("Cannot register controller '" + duplicated.getControllerName() + "'")
            && msg.contains(registered.getControllerName())
            && msg.contains(registered.getResourceTypeName()));
  }

  private static class TestControllerConfiguration<R extends HasMetadata>
      extends DefaultControllerConfiguration<R> {
    private final Reconciler<R> controller;

    public TestControllerConfiguration(Reconciler<R> controller, Class<R> crClass) {
      super(null, getControllerName(controller),
          CustomResource.getCRDName(crClass), null, false, null, null, null, null, crClass, null);
      this.controller = controller;
    }

    static <R extends HasMetadata> String getControllerName(
        Reconciler<R> controller) {
      return controller.getClass().getSimpleName() + "Controller";
    }

    private String getControllerName() {
      return getControllerName(controller);
    }
  }
}
