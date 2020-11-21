package io.javaoperatorsdk.operator;

import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.javaoperatorsdk.operator.sample.TestCustomResource;
import io.javaoperatorsdk.operator.sample.TestCustomResourceController;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ControllerUtilsTest {

    public static final String CUSTOM_FINALIZER_NAME = "a.custom/finalizer";

    @Test
    public void returnsValuesFromControllerAnnotationFinalizer() {
        final TestCustomResourceController controller = new TestCustomResourceController(null);
        Assertions.assertEquals(ControllerUtils.getDefaultFinalizerIdentifier(controller), ControllerUtils.getFinalizer(controller));
        assertEquals(TestCustomResource.class, ControllerUtils.getCustomResourceClass(controller));
        Assertions.assertEquals(TestCustomResourceController.CRD_NAME, ControllerUtils.getCrdName(controller));
        assertFalse(ControllerUtils.getGenerationEventProcessing(controller));
    }

    @Controller(crdName = "test.crd", customResourceClass = TestCustomResource.class, finalizerName = CUSTOM_FINALIZER_NAME)
    static class TestCustomFinalizerController implements ResourceController<TestCustomResource> {

        @Override
        public boolean deleteResource(TestCustomResource resource, Context<TestCustomResource> context) {
            return false;
        }

        @Override
        public UpdateControl<TestCustomResource> createOrUpdateResource(TestCustomResource resource, Context<TestCustomResource> context) {
            return null;
        }
    }

    @Test
    public void returnCustomerFinalizerNameIfSet() {
        assertEquals(CUSTOM_FINALIZER_NAME, ControllerUtils.getFinalizer(new TestCustomFinalizerController()));
    }
}
