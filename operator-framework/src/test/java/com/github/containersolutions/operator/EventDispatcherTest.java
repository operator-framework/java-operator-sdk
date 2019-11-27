package com.github.containersolutions.operator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import com.github.containersolutions.operator.processing.EventDispatcher;
import com.github.containersolutions.operator.sample.TestCustomResource;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Replaceable;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import io.fabric8.kubernetes.client.dsl.internal.RawCustomResourceOperationsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.HashMap;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class EventDispatcherTest {

    private CustomResource testCustomResource;
    private EventDispatcher<CustomResource> eventDispatcher;
    private ResourceController<CustomResource> resourceController = mock(ResourceController.class);
    private NonNamespaceOperation<CustomResource, CustomResourceList<CustomResource>,
            CustomResourceDoneable<CustomResource>,
            Resource<CustomResource, CustomResourceDoneable<CustomResource>>>
            operation = mock(NonNamespaceOperation.class);
    private KubernetesClient k8sClient = mock(KubernetesClient.class);
    private CustomResourceOperationsImpl<CustomResource, CustomResourceList<CustomResource>,
            CustomResourceDoneable<CustomResource>> resourceOperation = mock(CustomResourceOperationsImpl.class);

    private RawCustomResourceOperationsImpl rawResourceOperations = mock(RawCustomResourceOperationsImpl.class);
    @BeforeEach
    void setup() {
        eventDispatcher = new EventDispatcher(resourceController, resourceOperation, operation, k8sClient,
                Controller.DEFAULT_FINALIZER);

        testCustomResource = getResource();

        when(resourceController.createOrUpdateResource(eq(testCustomResource), any())).thenReturn(Optional.of(testCustomResource));
        when(resourceController.deleteResource(eq(testCustomResource), any())).thenReturn(true);
        when(resourceOperation.lockResourceVersion(any())).thenReturn(mock(Replaceable.class));

        // K8s client mocking
        when(k8sClient.customResource(any())).thenReturn(rawResourceOperations);
        when(rawResourceOperations.get(any(), any())).thenReturn(getRawResource());
    }

    @Test
    void callCreateOrUpdateOnNewResource() {
        eventDispatcher.handleEvent(Watcher.Action.ADDED, testCustomResource);
        verify(resourceController, times(1)).createOrUpdateResource(ArgumentMatchers.eq(testCustomResource), any());
    }

    @Test
    void callCreateOrUpdateOnModifiedResource() {
        eventDispatcher.handleEvent(Watcher.Action.MODIFIED, testCustomResource);
        verify(resourceController, times(1)).createOrUpdateResource(ArgumentMatchers.eq(testCustomResource), any());
    }

    @Test
    void adsDefaultFinalizerOnCreateIfNotThere() {
        eventDispatcher.handleEvent(Watcher.Action.MODIFIED, testCustomResource);
        verify(resourceController, times(1))
                .createOrUpdateResource(argThat(testCustomResource ->
                        testCustomResource.getMetadata().getFinalizers().contains(Controller.DEFAULT_FINALIZER)), any());
    }

    @Test
    void callsDeleteIfObjectHasFinalizerAndMarkedForDelete() {
        testCustomResource.getMetadata().setDeletionTimestamp("2019-8-10");
        testCustomResource.getMetadata().getFinalizers().add(Controller.DEFAULT_FINALIZER);

        eventDispatcher.handleEvent(Watcher.Action.MODIFIED, testCustomResource);

        verify(resourceController, times(1)).deleteResource(eq(testCustomResource), any());
    }

    /**
     * Note that there could be more finalizers. Out of our control.
     */
    @Test
    void doesNotCallDeleteOnControllerIfMarkedForDeletionButThereIsNoDefaultFinalizer() {
        markForDeletion(testCustomResource);

        eventDispatcher.handleEvent(Watcher.Action.MODIFIED, testCustomResource);

        verify(resourceController, never()).deleteResource(eq(testCustomResource), any());
    }

    @Test
    void removesDefaultFinalizerOnDelete() {
        markForDeletion(testCustomResource);
        testCustomResource.getMetadata().getFinalizers().add(Controller.DEFAULT_FINALIZER);

        eventDispatcher.handleEvent(Watcher.Action.MODIFIED, testCustomResource);

        assertEquals(0, testCustomResource.getMetadata().getFinalizers().size());
        verify(resourceOperation, times(1)).lockResourceVersion(any());
    }

    @Test
    void doesNotRemovesTheFinalizerIfTheDeleteMethodRemovesFalse() {
        when(resourceController.deleteResource(eq(testCustomResource), any())).thenReturn(false);
        markForDeletion(testCustomResource);
        testCustomResource.getMetadata().getFinalizers().add(Controller.DEFAULT_FINALIZER);

        eventDispatcher.handleEvent(Watcher.Action.MODIFIED, testCustomResource);

        assertEquals(1, testCustomResource.getMetadata().getFinalizers().size());
        verify(resourceOperation, never()).lockResourceVersion(any());
    }

    @Test
    void doesNotUpdateTheResourceIfEmptyOptionalReturned() {
        testCustomResource.getMetadata().getFinalizers().add(Controller.DEFAULT_FINALIZER);
        when(resourceController.createOrUpdateResource(eq(testCustomResource), any())).thenReturn(Optional.empty());

        eventDispatcher.handleEvent(Watcher.Action.MODIFIED, testCustomResource);

        verify(resourceOperation, never()).lockResourceVersion(any());
    }

    @Test
    void addsFinalizerIfNotMarkedForDeletionAndEmptyCustomResourceReturned() {
        when(resourceController.createOrUpdateResource(eq(testCustomResource), any())).thenReturn(Optional.empty());

        eventDispatcher.handleEvent(Watcher.Action.MODIFIED, testCustomResource);

        assertEquals(1, testCustomResource.getMetadata().getFinalizers().size());
        verify(resourceOperation, times(1)).lockResourceVersion(any());
    }

    @Test
    void doesNotAddFinalizerIfOptionalIsReturnedButMarkedForDeletion() {
        markForDeletion(testCustomResource);
        when(resourceController.createOrUpdateResource(eq(testCustomResource), any())).thenReturn(Optional.empty());

        eventDispatcher.handleEvent(Watcher.Action.MODIFIED, testCustomResource);

        assertEquals(0, testCustomResource.getMetadata().getFinalizers().size());
        verify(resourceOperation, never()).lockResourceVersion(any());
    }

    /**
     * When receiving a DELETE event the resource is already gone and we did the processing already on a MODIFIED
     * event received when then finalizer is added. The correct action on a DELETE event is a noop.
     */
    @Test
    void doesntDoAnythingOnDeleteEvent() {
        eventDispatcher.handleEvent(Watcher.Action.DELETED, testCustomResource);

        verify(k8sClient, never()).customResource(any());
    }


    private void markForDeletion(CustomResource customResource) {
        customResource.getMetadata().setDeletionTimestamp("2019-8-10");
    }

    CustomResource getResource() {
        TestCustomResource resource = new TestCustomResource();
        resource.setMetadata(new ObjectMetaBuilder()
                .withClusterName("clusterName")
                .withCreationTimestamp("creationTimestamp")
                .withDeletionGracePeriodSeconds(10L)
                .withGeneration(10L)
                .withName("name")
                .withNamespace("namespace")
                .withResourceVersion("resourceVersion")
                .withSelfLink("selfLink")
                .withUid("uid").build());
        return resource;
    }

    HashMap getRawResource() {
        return new ObjectMapper().convertValue(getResource(), HashMap.class);
    }
}
