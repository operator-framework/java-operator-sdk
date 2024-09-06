package io.javaoperatorsdk.operator.sample;

import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

import java.time.Duration;

public class ControllerNamespaceDeletionReconciler implements Reconciler<ControllerNamespaceDeletionCustomResource>,
        Cleaner<ControllerNamespaceDeletionCustomResource> {

    @Override
    public UpdateControl<ControllerNamespaceDeletionCustomResource> reconcile(ControllerNamespaceDeletionCustomResource resource,
                                                                              Context<ControllerNamespaceDeletionCustomResource> context) {


        return null;
    }


    @Override
    public DeleteControl cleanup(ControllerNamespaceDeletionCustomResource resource,
                                 Context<ControllerNamespaceDeletionCustomResource> context) {
        try {
            Thread.sleep(Duration.ofSeconds(10).toMillis());
            return DeleteControl.defaultDelete();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
