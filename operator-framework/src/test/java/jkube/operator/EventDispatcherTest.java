package jkube.operator;

import org.junit.jupiter.api.Test;

class EventDispatcherTest {

    // todo

    @Test
    public void callCreateOrUpdateOnNewResource() {

    }

    @Test
    public void callCreateOrUpdateOnModifiedResource() {

    }

    @Test
    public void adsDefaultFinalizerOnCreateIfNotThere() {

    }

    @Test
    public void callsDeleteIfObjectHasFinalizerAndMarkedForDeletition() {

    }

    /**
     * Note that there could be more finalizers. Out of our control.
     */
    @Test
    public void doesNotCallDeleteOnControllerIfMarkedForDeletionButThereIsNoDefaultFinalizer() {

    }

    @Test
    public void removesDefaultFinalizerOnDelete() {

    }

    @Test
    public void noExceptionIsThrowedOut() {

    }
}