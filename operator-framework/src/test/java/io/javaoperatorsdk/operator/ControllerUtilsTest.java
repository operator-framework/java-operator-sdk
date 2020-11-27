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
import io.javaoperatorsdk.operator.config.DefaultConfigurationService;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResourceController;
import org.junit.jupiter.api.Test;


class ControllerUtilsTest {

  public static final String CUSTOM_FINALIZER_NAME = "a.custom/finalizer";

  @Test
  public void returnsValuesFromControllerAnnotationFinalizer() {
    final var controller = new TestCustomResourceController(null);
        final var configuration = DefaultConfigurationService.instance().getConfigurationFor(controller);
        assertEquals(TestCustomResourceController.CRD_NAME, configuration.getCRDName());
        assertEquals(ControllerUtils.getDefaultFinalizerName(configuration.getCRDName()), configuration.getFinalizer());
    assertEquals(
        TestCustomResource.class,
        configuration.getCustomResourceClass());
    assertFalse(
        configuration.isGenerationAware());
    assertTrue(
        CustomResourceDoneable.class.isAssignableFrom(
            ControllerUtils.getCustomResourceDoneableClass(
                controller)));
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
    final var controller = new TestCustomFinalizerController();
        final var configuration = DefaultConfigurationService.instance().getConfigurationFor(controller);
        assertEquals(CUSTOM_FINALIZER_NAME, configuration.getFinalizer());
  }

  @Test
  public void supportsInnerClassCustomResources() {
    assertDoesNotThrow(
        () -> {
          ControllerUtils.getCustomResourceDoneableClass(new TestCustomFinalizerController());
        });
  }
}
