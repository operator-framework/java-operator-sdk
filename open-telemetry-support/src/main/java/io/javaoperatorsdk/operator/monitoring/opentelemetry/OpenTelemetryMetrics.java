package io.javaoperatorsdk.operator.monitoring.opentelemetry;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEvent;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;

public class OpenTelemetryMetrics implements Metrics {

  private Meter meter;
  private final boolean collectPerResourceMetrics;

  public OpenTelemetryMetrics(Meter meter, boolean collectPerResourceMetrics) {
    this.meter = meter;
    this.collectPerResourceMetrics = collectPerResourceMetrics;
  }

  @Override
  public void controllerRegistered(Controller<? extends HasMetadata> controller) {}

  @Override
  public void receivedEvent(Event event, Map<String, Object> metadata) {
    if (event instanceof ResourceEvent) {

    } else {

    }
  }

  @Override
  public void reconcileCustomResource(
      HasMetadata resource, RetryInfo retryInfo, Map<String, Object> metadata) {}

  @Override
  public void failedReconciliation(
      HasMetadata resource, Exception exception, Map<String, Object> metadata) {}

  @Override
  public void reconciliationExecutionStarted(HasMetadata resource, Map<String, Object> metadata) {}

  @Override
  public void reconciliationExecutionFinished(HasMetadata resource, Map<String, Object> metadata) {}

  @Override
  public void cleanupDoneFor(ResourceID resourceID, Map<String, Object> metadata) {}

  @Override
  public void finishedReconciliation(HasMetadata resource, Map<String, Object> metadata) {}

  @Override
  public <T> T timeControllerExecution(ControllerExecution<T> execution) throws Exception {
    return null;
  }

  @Override
  public <T extends Map<?, ?>> T monitorSizeOf(T map, String name) {
    return null;
  }

  private void incrementCounter(
      ResourceID id,
      String counterName,
      Map<String, Object> metadata,
      Map<String, String> additionalTags) {

    meter.counterBuilder("").buildWithCallback(m -> {}).close();

    Map<String, String> tags = new HashMap<>(additionalTags);
    addMetadataTags(id, metadata, tags, false);

    var counter = meter.counterBuilder(PREFIX + counterName).build();
    var attributes = Attributes.builder();
    additionalTags.forEach(attributes::put);
    counter.add(1, attributes.build());
  }

  private void addMetadataTags(
      ResourceID resourceID,
      Map<String, Object> metadata,
      Map<String, String> tags,
      boolean prefixed) {
    if (collectPerResourceMetrics) {
      addTag(NAME, resourceID.getName(), tags, prefixed);
      addTagOmittingOnEmptyValue(NAMESPACE, resourceID.getNamespace().orElse(null), tags, prefixed);
    }
    addTag(SCOPE, getScope(resourceID), tags, prefixed);
    final var gvk = (GroupVersionKind) metadata.get(Constants.RESOURCE_GVK_KEY);
    if (gvk != null) {
      addGVKTags(gvk, tags, prefixed);
    }
  }

  private static void addTag(
      String name, String value, Map<String, String> tags, boolean prefixed) {
    tags.put(getPrefixedMetadataTag(name, prefixed), value);
  }

  private static void addGVKTags(GroupVersionKind gvk, Map<String, String> tags, boolean prefixed) {
    addTagOmittingOnEmptyValue(GROUP, gvk.getGroup(), tags, prefixed);
    addTag(VERSION, gvk.getVersion(), tags, prefixed);
    addTag(KIND, gvk.getKind(), tags, prefixed);
  }

  private static String getPrefixedMetadataTag(String tagName, boolean prefixed) {
    return prefixed ? METADATA_PREFIX + tagName : tagName;
  }

  private static void addTagOmittingOnEmptyValue(
      String name, String value, Map<String, String> tags, boolean prefixed) {
    if (value != null && !value.isBlank()) {
      addTag(name, value, tags, prefixed);
    }
  }

  private static String getScope(ResourceID resourceID) {
    return resourceID.getNamespace().isPresent() ? NAMESPACE : CLUSTER;
  }
}
