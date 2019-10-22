package com.github.containersolutions.operator;

import com.github.containersolutions.operator.sample.TestCustomResource;
import com.github.containersolutions.operator.sample.TestCustomResourceController;
import com.github.containersolutions.operator.sample.TestCustomResourceDoneable;
import com.github.containersolutions.operator.sample.TestCustomResourceList;
import org.junit.jupiter.api.Test;

import static com.github.containersolutions.operator.api.Controller.DEFAULT_FINALIZER;
import static com.github.containersolutions.operator.api.Controller.DEFAULT_VERSION;
import static com.github.containersolutions.operator.sample.TestCustomResourceController.CRD_NAME;
import static com.github.containersolutions.operator.sample.TestCustomResourceController.TEST_GROUP;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ControllerUtilsTest {

    @Test
    public void returnsValuesFromControllerAnnotationFinalizer() {
        assertEquals(DEFAULT_FINALIZER, ControllerUtils.getDefaultFinalizer(new TestCustomResourceController()));
        assertEquals(TEST_GROUP + "/" + DEFAULT_VERSION, ControllerUtils.getApiVersion(new TestCustomResourceController()));
        assertEquals(DEFAULT_VERSION, ControllerUtils.getVersion(new TestCustomResourceController()));
        assertEquals(TestCustomResourceController.KIND_NAME, ControllerUtils.getKind(new TestCustomResourceController()));
        assertEquals(TestCustomResource.class, ControllerUtils.getCustomResourceClass(new TestCustomResourceController()));
        assertEquals(TestCustomResourceDoneable.class, ControllerUtils.getCustomResourceDonebaleClass(new TestCustomResourceController()));
        assertEquals(TestCustomResourceList.class, ControllerUtils.getCustomResourceListClass(new TestCustomResourceController()));
        assertEquals(CRD_NAME, ControllerUtils.getCrdName(new TestCustomResourceController()).get());
    }

}
