package io.javaoperatorsdk.operator.sample.customfilter;

import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.sample.AbstractExecutionNumberRecordingReconciler;

@ControllerConfiguration(eventFilters = {CustomFlagFilter.class, CustomFlagFilter2.class})
public class CustomFilteringTestReconciler extends
    AbstractExecutionNumberRecordingReconciler<CustomFilteringTestResource> {
}
