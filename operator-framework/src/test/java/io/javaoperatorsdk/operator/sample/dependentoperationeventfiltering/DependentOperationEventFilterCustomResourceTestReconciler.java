package io.javaoperatorsdk.operator.sample.dependentoperationeventfiltering;

import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.sample.AbstractExecutionNumberRecordingReconciler;

@ControllerConfiguration(
    namespaces = Constants.WATCH_CURRENT_NAMESPACE,
    dependents = {
        @Dependent(type = ConfigMapDependentResource.class),
    })
public class DependentOperationEventFilterCustomResourceTestReconciler
    extends
    AbstractExecutionNumberRecordingReconciler<DependentOperationEventFilterCustomResource> {
}
