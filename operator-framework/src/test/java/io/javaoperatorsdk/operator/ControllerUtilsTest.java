package io.javaoperatorsdk.operator;

import io.javaoperatorsdk.operator.sample.TestCustomResource;
import io.javaoperatorsdk.operator.sample.TestCustomResourceController;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.javaoperatorsdk.operator.api.Controller;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerUtilsTest {

    @Test
    public void returnsValuesFromControllerAnnotationFinalizer() {
        Assertions.assertEquals(Controller.DEFAULT_FINALIZER, ControllerUtils.getDefaultFinalizer(new TestCustomResourceController(null)));
        assertEquals(TestCustomResource.class, ControllerUtils.getCustomResourceClass(new TestCustomResourceController(null)));
        Assertions.assertEquals(TestCustomResourceController.CRD_NAME, ControllerUtils.getCrdName(new TestCustomResourceController(null)));
        assertEquals(false, ControllerUtils.getGenerationEventProcessing(new TestCustomResourceController(null)));
        assertTrue(CustomResourceDoneable.class.isAssignableFrom(ControllerUtils.getCustomResourceDoneableClass(new TestCustomResourceController(null))));
    }
}
