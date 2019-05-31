package com.github.containersolutions.operator;

import com.github.containersolutions.operator.sample.TestCustomResource;
import com.github.containersolutions.operator.sample.TestResourceController;
import org.junit.jupiter.api.Test;

import static com.github.containersolutions.operator.api.Controller.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ControllerUtilsTest {

    @Test
    public void returnsValuesFromControllerAnnotationFinalizer() {
        assertEquals(DEFAULT_FINALIZER, ControllerUtils.getDefaultFinalizer(new TestResourceController()));
        assertEquals(DEFAULT_API_EXTENSION_VERSION, ControllerUtils.getApiVersion(new TestResourceController()));
        assertEquals(DEFAULT_API_VERSION, ControllerUtils.getCrdVersion(new TestResourceController()));
        assertEquals(TestResourceController.KIND_NAME, ControllerUtils.getKind(new TestResourceController()));
        assertEquals(TestCustomResource.class, ControllerUtils.getCustomResourceClass(new TestResourceController()));
    }

}