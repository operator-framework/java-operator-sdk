package io.javaoperatorsdk.operator;

import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.javaoperatorsdk.operator.sample.TestCustomResource;
import io.javaoperatorsdk.operator.sample.TestCustomResourceController;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerUtilsTest {

    public static final String CUSTOM_FINALIZER_NAME = "a.customer.finalizer";

    @Test
    public void returnsValuesFromControllerAnnotationFinalizer() {
        Assertions.assertEquals(TestCustomResourceController.class.getCanonicalName(), ControllerUtils.getFinalizer(new TestCustomResourceController(null)));
        assertEquals(TestCustomResource.class, ControllerUtils.getCustomResourceClass(new TestCustomResourceController(null)));
        Assertions.assertEquals(TestCustomResourceController.CRD_NAME, ControllerUtils.getCrdName(new TestCustomResourceController(null)));
        assertEquals(false, ControllerUtils.getGenerationEventProcessing(new TestCustomResourceController(null)));
        assertTrue(CustomResourceDoneable.class.isAssignableFrom(ControllerUtils.getCustomResourceDoneableClass(new TestCustomResourceController(null))));
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
