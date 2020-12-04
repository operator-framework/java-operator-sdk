package io.javaoperatorsdk.operator.config.runtime;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ControllerUtils;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultConfigurationServiceTest {
    public static final String CUSTOM_FINALIZER_NAME = "a.custom/finalizer";
    
    @Test
    public void returnsValuesFromControllerAnnotationFinalizer() {
        final var controller = new TestCustomResourceController();
        final var configuration = DefaultConfigurationService.instance().getConfigurationFor(controller);
        assertEquals(TestCustomResourceController.CRD_NAME, configuration.getCRDName());
        assertEquals(ControllerUtils.getDefaultFinalizerName(configuration.getCRDName()), configuration.getFinalizer());
        assertEquals(TestCustomResource.class, configuration.getCustomResourceClass());
        assertFalse(configuration.isGenerationAware());
        assertTrue(CustomResourceDoneable.class.isAssignableFrom(configuration.getDoneableClass()));
    }
    
    @Test
    public void returnCustomerFinalizerNameIfSet() {
        final var controller = new TestCustomFinalizerController();
        final var configuration = DefaultConfigurationService.instance().getConfigurationFor(controller);
        assertEquals(CUSTOM_FINALIZER_NAME, configuration.getFinalizer());
    }
    
    @Test
    public void supportsInnerClassCustomResources() {
        final var controller = new TestCustomFinalizerController();
        assertDoesNotThrow(
            () -> {
                DefaultConfigurationService.instance().getConfigurationFor(controller).getCustomResourceDoneableClass();
            });
    }
    
    @Controller(crdName = "test.crd", finalizerName = CUSTOM_FINALIZER_NAME)
    static class TestCustomFinalizerController
        implements ResourceController<TestCustomFinalizerController.InnerCustomResource> {
        public class InnerCustomResource extends CustomResource {
        }
        
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
    @Controller(generationAwareEventProcessing = false, crdName = TestCustomResourceController.CRD_NAME)
    static class TestCustomResourceController implements ResourceController<TestCustomResource> {
        
        public static final String CRD_NAME = "customservices.sample.javaoperatorsdk";
        public static final String FINALIZER_NAME = CRD_NAME + "/finalizer";
    
        @Override
        public DeleteControl deleteResource(TestCustomResource resource, Context<TestCustomResource> context) {
            return DeleteControl.DEFAULT_DELETE;
        }
        
        @Override
        public UpdateControl<TestCustomResource> createOrUpdateResource(TestCustomResource resource, Context<TestCustomResource> context) {
            return null;
        }
    }
    
    static class TestCustomResource extends CustomResource {
    }
    
}
