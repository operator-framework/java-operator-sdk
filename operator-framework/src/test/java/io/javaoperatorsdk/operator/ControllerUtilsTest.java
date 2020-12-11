package io.javaoperatorsdk.operator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResourceController;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ControllerUtilsTest {

  public static final String CUSTOM_FINALIZER_NAME = "a.custom/finalizer";

  @Test
  public void returnsValuesFromControllerAnnotationFinalizer() {
    Assertions.assertEquals(
        TestCustomResourceController.CRD_NAME + "/finalizer",
        ControllerUtils.getFinalizer(new TestCustomResourceController(null)));
    assertEquals(
        TestCustomResource.class,
        ControllerUtils.getCustomResourceClass(new TestCustomResourceController(null)));
    Assertions.assertEquals(
        TestCustomResourceController.CRD_NAME,
        ControllerUtils.getCrdName(new TestCustomResourceController(null)));
    assertFalse(
        ControllerUtils.getGenerationEventProcessing(new TestCustomResourceController(null)));
    assertTrue(
        CustomResourceDoneable.class.isAssignableFrom(
            ControllerUtils.getCustomResourceDoneableClass(
                new TestCustomResourceController(null))));
  }

  @Controller(crdName = "test.crd", finalizerName = CUSTOM_FINALIZER_NAME)
  static class TestCustomFinalizerController
      implements ResourceController<TestCustomFinalizerController.InnerCustomResource> {
    public class InnerCustomResource extends CustomResource {}

    @Override
    public DeleteControl deleteResource(
        TestCustomFinalizerController.InnerCustomResource resource,
        Context<InnerCustomResource> context) {
      return DeleteControl.DEFAULT_DELETE;
    }

    @Override
    public UpdateControl<TestCustomFinalizerController.InnerCustomResource> createOrUpdateResource(
        InnerCustomResource resource, Context<InnerCustomResource> context) {
      return null;
    }
  }

  @Test
  public void returnCustomerFinalizerNameIfSet() {
    assertEquals(
        CUSTOM_FINALIZER_NAME, ControllerUtils.getFinalizer(new TestCustomFinalizerController()));
  }

  @Test
  public void supportsInnerClassCustomResources() {
    assertDoesNotThrow(
        () -> {
          ControllerUtils.getCustomResourceDoneableClass(new TestCustomFinalizerController());
        });
  }
}
