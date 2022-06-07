package io.javaoperatorsdk.operator.sample.orderedmanageddependent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.sample.AbstractExecutionNumberRecordingReconciler;

@ControllerConfiguration(
    namespaces = Constants.WATCH_CURRENT_NAMESPACE,
    dependents = {
        @Dependent(type = ConfigMapDependentResource1.class),
        @Dependent(type = ConfigMapDependentResource2.class)
    })
public class OrderedManagedDependentTestReconciler
    extends AbstractExecutionNumberRecordingReconciler<OrderedManagedDependentCustomResource> {

  public static final List<Class<?>> dependentExecution =
      Collections.synchronizedList(new ArrayList<>());
}
