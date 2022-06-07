package io.javaoperatorsdk.operator.sample.cleanermanageddependent;

import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.sample.AbstractExecutionNumberRecordingReconciler;

@ControllerConfiguration(dependents = {@Dependent(type = ConfigMapDependentResource.class)})
public class CleanerForManagedDependentTestReconciler
    extends AbstractExecutionNumberRecordingReconciler<CleanerForManagedDependentCustomResource> {
}
