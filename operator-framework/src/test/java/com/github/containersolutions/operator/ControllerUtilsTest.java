package com.github.containersolutions.operator;

import com.github.containersolutions.operator.sample.TestCustomResource;
import com.github.containersolutions.operator.sample.TestCustomResourceController;
import com.github.containersolutions.operator.sample.TestCustomResourceDoneable;
import com.github.containersolutions.operator.sample.TestCustomResourceList;
import org.junit.jupiter.api.Test;

import static com.github.containersolutions.operator.api.Controller.DEFAULT_FINALIZER;
import static com.github.containersolutions.operator.sample.TestCustomResourceController.CRD_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ControllerUtilsTest {

    @Test
    public void returnsValuesFromControllerAnnotationFinalizer() {
        assertEquals(DEFAULT_FINALIZER, ControllerUtils.getDefaultFinalizer(new TestCustomResourceController(null)));
        assertEquals(TestCustomResource.class, ControllerUtils.getCustomResourceClass(new TestCustomResourceController(null)));
        assertEquals(TestCustomResourceDoneable.class, ControllerUtils.getCustomResourceDonebaleClass(new TestCustomResourceController(null)));
        assertEquals(TestCustomResourceList.class, ControllerUtils.getCustomResourceListClass(new TestCustomResourceController(null)));
        assertEquals(CRD_NAME, ControllerUtils.getCrdName(new TestCustomResourceController(null)));
    }

    @Test
    public void createsCustomResourceDoneableImplementation() {
        Class result = ControllerUtils.createDoneableClassForCustomResource(TestCustomResource.class);
        assertThat(result.getSimpleName()).isEqualTo(TestCustomResource.class.getSimpleName() + "Doneable");
    }

}
