package io.javaoperatorsdk.operator.baseapi.primaryindexer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration
public class AbstractPrimaryIndexerTestReconciler
    implements Reconciler<PrimaryIndexerTestCustomResource> {

  public static final String CONFIG_MAP_NAME = "common-config-map";

  private final Map<String, AtomicInteger> numberOfExecutions = new ConcurrentHashMap<>();

  protected static final String CONFIG_MAP_RELATION_INDEXER = "cm-indexer";

  protected static final Function<PrimaryIndexerTestCustomResource, List<String>> indexer =
      resource -> List.of(resource.getSpec().getConfigMapName());

  @Override
  public UpdateControl<PrimaryIndexerTestCustomResource> reconcile(
      PrimaryIndexerTestCustomResource resource,
      Context<PrimaryIndexerTestCustomResource> context) {
    numberOfExecutions.computeIfAbsent(resource.getMetadata().getName(), r -> new AtomicInteger(0));
    numberOfExecutions.get(resource.getMetadata().getName()).incrementAndGet();
    return UpdateControl.noUpdate();
  }

  public Map<String, AtomicInteger> getNumberOfExecutions() {
    return numberOfExecutions;
  }
}
