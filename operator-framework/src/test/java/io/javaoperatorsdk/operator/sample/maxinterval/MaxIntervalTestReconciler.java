package io.javaoperatorsdk.operator.sample.maxinterval;

import java.util.concurrent.TimeUnit;

import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ReconciliationMaxInterval;
import io.javaoperatorsdk.operator.sample.AbstractExecutionNumberRecordingReconciler;

@ControllerConfiguration(reconciliationMaxInterval = @ReconciliationMaxInterval(interval = 50,
    timeUnit = TimeUnit.MILLISECONDS))
public class MaxIntervalTestReconciler
    extends AbstractExecutionNumberRecordingReconciler<MaxIntervalTestCustomResource> {
}
