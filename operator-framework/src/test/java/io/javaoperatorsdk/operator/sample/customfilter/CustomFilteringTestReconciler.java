package io.javaoperatorsdk.operator.sample.customfilter;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.sample.observedgeneration.ObservedGenerationTestCustomResource;

import java.util.concurrent.atomic.AtomicInteger;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_FINALIZER;

@ControllerConfiguration(eventFilters = CustomFlagFilter.class)
public class CustomFilteringTestReconciler implements Reconciler<CustomFilteringTestResource> {

    private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

    @Override
    public UpdateControl<CustomFilteringTestResource> reconcile(CustomFilteringTestResource resource, Context context) {
        numberOfExecutions.incrementAndGet();
        if (!resource.getSpec().isReconcile()) {
            throw new IllegalStateException("This should not happen!");
        }
        return UpdateControl.noUpdate();
    }
}
