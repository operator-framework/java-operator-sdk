package com.github.containersolutions.operator;

import com.github.containersolutions.operator.api.ResourceController;
import com.github.containersolutions.operator.api.UpdateControl;
import com.github.containersolutions.operator.processing.CustomResourceEvent;
import com.github.containersolutions.operator.processing.EventDispatcher;
import com.github.containersolutions.operator.processing.retry.GenericRetry;
import com.github.containersolutions.operator.sample.TestCustomResource;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import static com.github.containersolutions.operator.api.Controller.DEFAULT_FINALIZER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class EventDispatcherTest {

    private CustomResource testCustomResource;
    private EventDispatcher eventDispatcher;
    private ResourceController<CustomResource> resourceController = mock(ResourceController.class);
    private EventDispatcher.CustomResourceFacade customResourceFacade = mock(EventDispatcher.CustomResourceFacade.class);

    @BeforeEach
    void setup() {
        eventDispatcher = new EventDispatcher(resourceController,
                DEFAULT_FINALIZER, customResourceFacade);

        testCustomResource = getResource();

        when(resourceController.createOrUpdateResource(eq(testCustomResource), any())).thenReturn(UpdateControl.updateCustomResource(testCustomResource));
        when(resourceController.deleteResource(eq(testCustomResource), any())).thenReturn(true);
        when(customResourceFacade.replaceWithLock(any())).thenReturn(null);
    }

    @Test
    void callCreateOrUpdateOnNewResource() {
        eventDispatcher.handleEvent(customResourceEvent(Watcher.Action.ADDED, testCustomResource));
        verify(resourceController, times(1)).createOrUpdateResource(ArgumentMatchers.eq(testCustomResource), any());
    }

    @Test
    void updatesOnlyStatusSubResource() {
        testCustomResource.getMetadata().getFinalizers().add(DEFAULT_FINALIZER);
        when(resourceController.createOrUpdateResource(eq(testCustomResource), any()))
                .thenReturn(UpdateControl.updateStatusSubResource(testCustomResource));

        eventDispatcher.handleEvent(customResourceEvent(Watcher.Action.ADDED, testCustomResource));

        verify(customResourceFacade, times(1)).updateStatus(testCustomResource);
        verify(customResourceFacade, never()).replaceWithLock(any());
    }

    @Test
    void updateStatusSubResourceAndCustomResource() {
        testCustomResource.getMetadata().getFinalizers().add(DEFAULT_FINALIZER);
        when(resourceController.createOrUpdateResource(eq(testCustomResource), any()))
                .thenReturn(UpdateControl.updateStatusAndCustomResource(testCustomResource));

        eventDispatcher.handleEvent(customResourceEvent(Watcher.Action.ADDED, testCustomResource));

        verify(customResourceFacade, times(1)).updateStatus(testCustomResource);
        verify(customResourceFacade, times(1)).replaceWithLock(any());
    }

    @Test
    void callCreateOrUpdateOnModifiedResource() {
        eventDispatcher.handleEvent(customResourceEvent(Watcher.Action.MODIFIED, testCustomResource));
        verify(resourceController, times(1)).createOrUpdateResource(ArgumentMatchers.eq(testCustomResource), any());
    }

    @Test
    void adsDefaultFinalizerOnCreateIfNotThere() {
        eventDispatcher.handleEvent(customResourceEvent(Watcher.Action.MODIFIED, testCustomResource));
        verify(resourceController, times(1))
                .createOrUpdateResource(argThat(testCustomResource ->
                        testCustomResource.getMetadata().getFinalizers().contains(DEFAULT_FINALIZER)), any());
    }

    @Test
    void callsDeleteIfObjectHasFinalizerAndMarkedForDelete() {
        testCustomResource.getMetadata().setDeletionTimestamp("2019-8-10");
        testCustomResource.getMetadata().getFinalizers().add(DEFAULT_FINALIZER);

        eventDispatcher.handleEvent(customResourceEvent(Watcher.Action.MODIFIED, testCustomResource));

        verify(resourceController, times(1)).deleteResource(eq(testCustomResource), any());
    }

    /**
     * Note that there could be more finalizers. Out of our control.
     */
    @Test
    void callDeleteOnControllerIfMarkedForDeletionButThereIsNoDefaultFinalizer() {
        markForDeletion(testCustomResource);

        eventDispatcher.handleEvent(customResourceEvent(Watcher.Action.MODIFIED, testCustomResource));

        verify(resourceController).deleteResource(eq(testCustomResource), any());
    }

    @Test
    void removesDefaultFinalizerOnDelete() {
        markForDeletion(testCustomResource);
        testCustomResource.getMetadata().getFinalizers().add(DEFAULT_FINALIZER);

        eventDispatcher.handleEvent(customResourceEvent(Watcher.Action.MODIFIED, testCustomResource));

        assertEquals(0, testCustomResource.getMetadata().getFinalizers().size());
        verify(customResourceFacade, times(1)).replaceWithLock(any());
    }

    @Test
    void doesNotRemovesTheFinalizerIfTheDeleteMethodRemovesFalse() {
        when(resourceController.deleteResource(eq(testCustomResource), any())).thenReturn(false);
        markForDeletion(testCustomResource);
        testCustomResource.getMetadata().getFinalizers().add(DEFAULT_FINALIZER);

        eventDispatcher.handleEvent(customResourceEvent(Watcher.Action.MODIFIED, testCustomResource));

        assertEquals(1, testCustomResource.getMetadata().getFinalizers().size());
        verify(customResourceFacade, never()).replaceWithLock(any());
    }

    @Test
    void doesNotUpdateTheResourceIfNoUpdateUpdateControl() {
        testCustomResource.getMetadata().getFinalizers().add(DEFAULT_FINALIZER);
        when(resourceController.createOrUpdateResource(eq(testCustomResource), any())).thenReturn(UpdateControl.noUpdate());

        eventDispatcher.handleEvent(customResourceEvent(Watcher.Action.MODIFIED, testCustomResource));
        verify(customResourceFacade, never()).replaceWithLock(any());
        verify(customResourceFacade, never()).updateStatus(testCustomResource);
    }

    @Test
    void addsFinalizerIfNotMarkedForDeletionAndEmptyCustomResourceReturned() {
        when(resourceController.createOrUpdateResource(eq(testCustomResource), any())).thenReturn(UpdateControl.noUpdate());

        eventDispatcher.handleEvent(customResourceEvent(Watcher.Action.MODIFIED, testCustomResource));

        assertEquals(1, testCustomResource.getMetadata().getFinalizers().size());
        verify(customResourceFacade, times(1)).replaceWithLock(any());
    }

    @Test
    void doesNotAddFinalizerIfNoUpdateIsReturnedButMarkedForDeletion() {
        markForDeletion(testCustomResource);
        when(resourceController.createOrUpdateResource(eq(testCustomResource), any())).thenReturn(UpdateControl.noUpdate());

        eventDispatcher.handleEvent(customResourceEvent(Watcher.Action.MODIFIED, testCustomResource));

        assertEquals(0, testCustomResource.getMetadata().getFinalizers().size());
        verify(customResourceFacade, never()).replaceWithLock(any());
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

    public CustomResourceEvent customResourceEvent(Watcher.Action action, CustomResource resource) {
        return new CustomResourceEvent(action, resource, GenericRetry.defaultLimitedExponentialRetry());
    }
}
