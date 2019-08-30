package com.github.containersolutions.operator;

import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import com.github.containersolutions.operator.sample.TestCustomResource;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class EventDispatcherTest {

    TestCustomResource testCustomResource;
    private EventDispatcher<TestCustomResource> eventDispatcher;
    private ResourceController<TestCustomResource> resourceController = mock(ResourceController.class);
    private NonNamespaceOperation<TestCustomResource, CustomResourceList<TestCustomResource>,
            CustomResourceDoneable<TestCustomResource>,
            Resource<TestCustomResource, CustomResourceDoneable<TestCustomResource>>>
            operation = mock(NonNamespaceOperation.class);
    private KubernetesClient k8sClient = mock(KubernetesClient.class);
    private CustomResourceOperationsImpl<TestCustomResource, CustomResourceList<TestCustomResource>,
            CustomResourceDoneable<TestCustomResource>> resourceOperation = mock(CustomResourceOperationsImpl.class);

    @BeforeEach
    public void setup() {
        eventDispatcher = new EventDispatcher(resourceController, resourceOperation, operation, k8sClient,
                Controller.DEFAULT_FINALIZER);

        testCustomResource = new TestCustomResource();
        testCustomResource.setMetadata(new ObjectMeta());
        testCustomResource.getMetadata().setFinalizers(new ArrayList<>());

        when(resourceController.createOrUpdateResource(eq(testCustomResource), any())).thenReturn(Optional.of(testCustomResource));
        when(resourceController.deleteResource(eq(testCustomResource), any())).thenReturn(true);
    }

    @Test
    public void callCreateOrUpdateOnNewResource() {
        eventDispatcher.handleEvent(Watcher.Action.ADDED, testCustomResource);
        verify(resourceController, times(1)).createOrUpdateResource(ArgumentMatchers.eq(testCustomResource), any());
    }

    @Test
    public void callCreateOrUpdateOnModifiedResource() {
        eventDispatcher.handleEvent(Watcher.Action.MODIFIED, testCustomResource);
        verify(resourceController, times(1)).createOrUpdateResource(ArgumentMatchers.eq(testCustomResource), any());
    }

    @Test
    public void adsDefaultFinalizerOnCreateIfNotThere() {
        eventDispatcher.handleEvent(Watcher.Action.MODIFIED, testCustomResource);
        verify(resourceController, times(1)).createOrUpdateResource(argThat(new ArgumentMatcher<TestCustomResource>() {
            @Override
            public boolean matches(TestCustomResource testCustomResource) {
                return testCustomResource.getMetadata().getFinalizers().contains(Controller.DEFAULT_FINALIZER);
            }
        }), any());
    }

    @Test
    public void callsDeleteIfObjectHasFinalizerAndMarkedForDelete() {
        testCustomResource.getMetadata().setDeletionTimestamp("2019-8-10");
        testCustomResource.getMetadata().getFinalizers().add(Controller.DEFAULT_FINALIZER);

        eventDispatcher.handleEvent(Watcher.Action.MODIFIED, testCustomResource);

        verify(resourceController, times(1)).deleteResource(eq(testCustomResource), any());
    }

    /**
     * Note that there could be more finalizers. Out of our control.
     */
    @Test
    public void doesNotCallDeleteOnControllerIfMarkedForDeletionButThereIsNoDefaultFinalizer() {
        markForDeletion(testCustomResource);

        eventDispatcher.handleEvent(Watcher.Action.MODIFIED, testCustomResource);

        verify(resourceController, never()).deleteResource(eq(testCustomResource), any());
    }

    @Test
    public void removesDefaultFinalizerOnDelete() {
        markForDeletion(testCustomResource);
        testCustomResource.getMetadata().getFinalizers().add(Controller.DEFAULT_FINALIZER);

        eventDispatcher.handleEvent(Watcher.Action.MODIFIED, testCustomResource);

        assertEquals(0, testCustomResource.getMetadata().getFinalizers().size());
        verify(resourceOperation, times(1)).lockResourceVersion(any());
    }

    @Test
    public void doesNotRemovesTheFinalizerIfTheDeleteMethodRemovesFalse() {
        when(resourceController.deleteResource(eq(testCustomResource), any())).thenReturn(false);
        markForDeletion(testCustomResource);
        testCustomResource.getMetadata().getFinalizers().add(Controller.DEFAULT_FINALIZER);

        eventDispatcher.handleEvent(Watcher.Action.MODIFIED, testCustomResource);

        assertEquals(1, testCustomResource.getMetadata().getFinalizers().size());
        verify(resourceOperation, never()).lockResourceVersion(any());
    }

    @Test
    public void doesNotUpdateTheResourceIfEmptyOptionalReturned() {
        testCustomResource.getMetadata().getFinalizers().add(Controller.DEFAULT_FINALIZER);
        when(resourceController.createOrUpdateResource(eq(testCustomResource), any())).thenReturn(Optional.empty());

        eventDispatcher.handleEvent(Watcher.Action.MODIFIED, testCustomResource);

        verify(resourceOperation, never()).lockResourceVersion(any());
    }

    @Test
    public void addsFinalizerIfNotMarkedForDeletionAndEmptyCustomResourceReturned() {
        when(resourceController.createOrUpdateResource(eq(testCustomResource), any())).thenReturn(Optional.empty());

        eventDispatcher.handleEvent(Watcher.Action.MODIFIED, testCustomResource);

        assertEquals(1, testCustomResource.getMetadata().getFinalizers().size());
        verify(resourceOperation, times(1)).lockResourceVersion(any());
    }

    @Test
    public void doesNotAddFinalizerIfOptionalIsReturnedButMarkedForDeletion() {
        markForDeletion(testCustomResource);
        when(resourceController.createOrUpdateResource(eq(testCustomResource), any())).thenReturn(Optional.empty());

        eventDispatcher.handleEvent(Watcher.Action.MODIFIED, testCustomResource);

        assertEquals(0, testCustomResource.getMetadata().getFinalizers().size());
        verify(resourceOperation, never()).lockResourceVersion(any());
    }

    private void markForDeletion(CustomResource customResource) {
        customResource.getMetadata().setDeletionTimestamp("2019-8-10");
    }

}