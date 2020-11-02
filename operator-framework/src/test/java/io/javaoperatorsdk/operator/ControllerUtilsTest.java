package com.github.containersolutions.operator;

import com.github.containersolutions.operator.sample.TestCustomResource;
import com.github.containersolutions.operator.sample.TestCustomResourceController;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import org.junit.jupiter.api.Test;

import static com.github.containersolutions.operator.api.Controller.DEFAULT_FINALIZER;
import static com.github.containersolutions.operator.sample.TestCustomResourceController.CRD_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerUtilsTest {

    @Test
    public void returnsValuesFromControllerAnnotationFinalizer() {
        assertEquals(DEFAULT_FINALIZER, ControllerUtils.getDefaultFinalizer(new TestCustomResourceController(null)));
        assertEquals(TestCustomResource.class, ControllerUtils.getCustomResourceClass(new TestCustomResourceController(null)));
        assertEquals(CRD_NAME, ControllerUtils.getCrdName(new TestCustomResourceController(null)));
        assertEquals(false, ControllerUtils.getGenerationEventProcessing(new TestCustomResourceController(null)));
        assertTrue(CustomResourceDoneable.class.isAssignableFrom(ControllerUtils.getCustomResourceDoneableClass(new TestCustomResourceController(null))));
    }
}
