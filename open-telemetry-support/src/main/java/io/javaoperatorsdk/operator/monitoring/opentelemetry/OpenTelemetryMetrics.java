package io.javaoperatorsdk.operator.monitoring.opentelemetry;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

import java.util.Map;

public class OpenTelemetryMetrics implements Metrics {
    
    @Override
    public void controllerRegistered(Controller<? extends HasMetadata> controller) {
        Metrics.super.controllerRegistered(controller);
    }

    @Override
    public void receivedEvent(Event event, Map<String, Object> metadata) {
        Metrics.super.receivedEvent(event, metadata);
    }

    @Override
    public void reconcileCustomResource(HasMetadata resource, RetryInfo retryInfo, Map<String, Object> metadata) {
        Metrics.super.reconcileCustomResource(resource, retryInfo, metadata);
    }

    @Override
    public void failedReconciliation(HasMetadata resource, Exception exception, Map<String, Object> metadata) {
        Metrics.super.failedReconciliation(resource, exception, metadata);
    }

    @Override
    public void reconciliationExecutionStarted(HasMetadata resource, Map<String, Object> metadata) {
        Metrics.super.reconciliationExecutionStarted(resource, metadata);
    }

    @Override
    public void reconciliationExecutionFinished(HasMetadata resource, Map<String, Object> metadata) {
        Metrics.super.reconciliationExecutionFinished(resource, metadata);
    }

    @Override
    public void cleanupDoneFor(ResourceID resourceID, Map<String, Object> metadata) {
        Metrics.super.cleanupDoneFor(resourceID, metadata);
    }

    @Override
    public void finishedReconciliation(HasMetadata resource, Map<String, Object> metadata) {
        Metrics.super.finishedReconciliation(resource, metadata);
    }

    @Override
    public <T> T timeControllerExecution(ControllerExecution<T> execution) throws Exception {
        return Metrics.super.timeControllerExecution(execution);
    }

    @Override
    public <T extends Map<?, ?>> T monitorSizeOf(T map, String name) {
        return Metrics.super.monitorSizeOf(map, name);
    }
}
