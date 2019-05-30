package jkube.operator;

import jkube.operator.sample.TestCustomResource;
import jkube.operator.sample.TestResourceController;
import org.junit.jupiter.api.Test;

import static jkube.operator.api.Controller.*;
import static jkube.operator.sample.TestResourceController.CUSTOM_RESOURCE_DEFINITION_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ControllerUtilsTest {

    @Test
    public void returnsValuesFromControllerAnnotationFinalizer() {
        assertEquals(DEFAULT_FINALIZER, ControllerUtils.getDefaultFinalizer(new TestResourceController()));
        assertEquals(DEFAULT_API_EXTENSION_VERSION, ControllerUtils.getApiVersion(new TestResourceController()));
        assertEquals(DEFAULT_API_VERSION, ControllerUtils.getCrdVersion(new TestResourceController()));
        assertEquals(CUSTOM_RESOURCE_DEFINITION_NAME, ControllerUtils.getCustomResourceDefinitionName(new TestResourceController()));
        assertEquals(TestCustomResource.class, ControllerUtils.getCustomResourceClass(new TestResourceController()));
    }

}