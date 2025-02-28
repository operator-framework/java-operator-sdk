package io.javaoperatorsdk.operator.dependent.primarytosecondaydependent;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryToSecondaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

import static io.javaoperatorsdk.operator.dependent.primarytosecondaydependent.PrimaryToSecondaryDependentReconciler.CONFIG_MAP;
import static io.javaoperatorsdk.operator.dependent.primarytosecondaydependent.PrimaryToSecondaryDependentReconciler.CONFIG_MAP_EVENT_SOURCE;

/**
 * Sample showcases how it is possible to do a primary to secondary mapper for a dependent resource.
 * Note that this is usually just used with read only resources. So it has limited usage, one reason
 * to use it is to have nice condition on that resource within a workflow.
 */
@Workflow(
    dependents = {
      @Dependent(
          type = ConfigMapDependent.class,
          name = CONFIG_MAP,
          reconcilePrecondition = ConfigMapReconcilePrecondition.class,
          useEventSourceWithName = CONFIG_MAP_EVENT_SOURCE),
      @Dependent(type = SecretDependent.class, dependsOn = CONFIG_MAP)
    })
@ControllerConfiguration()
public class PrimaryToSecondaryDependentReconciler
    implements Reconciler<PrimaryToSecondaryDependentCustomResource>, TestExecutionInfoProvider {

  public static final String DATA_KEY = "data";
  public static final String CONFIG_MAP = "ConfigMap";
  public static final String CONFIG_MAP_INDEX = "ConfigMapIndex";
  public static final String CONFIG_MAP_EVENT_SOURCE = "ConfigMapEventSource";

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<PrimaryToSecondaryDependentCustomResource> reconcile(
      PrimaryToSecondaryDependentCustomResource resource,
      Context<PrimaryToSecondaryDependentCustomResource> context) {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  /**
   * Creating an Event Source and setting it for the Dependent Resource. Since it is not possible to
   * do this setup elegantly within the bounds of the KubernetesDependentResource API. However, this
   * is quite a corner case; might be covered more out of the box in the future if there will be
   * demand for it.
   */
  @Override
  public List<EventSource<?, PrimaryToSecondaryDependentCustomResource>> prepareEventSources(
      EventSourceContext<PrimaryToSecondaryDependentCustomResource> context) {
    // there is no owner reference in the config map, but we still want to trigger reconciliation if
    // the config map changes. So first we add an index which custom resource references the config
    // map.
    context
        .getPrimaryCache()
        .addIndexer(
            CONFIG_MAP_INDEX,
            (primary ->
                List.of(
                    indexKey(
                        primary.getSpec().getConfigMapName(),
                        primary.getMetadata().getNamespace()))));

    var es =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(
                    ConfigMap.class, PrimaryToSecondaryDependentCustomResource.class)
                .withName(CONFIG_MAP_EVENT_SOURCE)
                // if there is a many-to-many relationship (thus no direct owner reference)
                // PrimaryToSecondaryMapper needs to be added
                .withPrimaryToSecondaryMapper(
                    (PrimaryToSecondaryMapper<PrimaryToSecondaryDependentCustomResource>)
                        p ->
                            Set.of(
                                new ResourceID(
                                    p.getSpec().getConfigMapName(),
                                    p.getMetadata().getNamespace())))
                // the index is used to trigger reconciliation of related custom resources if config
                // map
                // changes
                .withSecondaryToPrimaryMapper(
                    cm ->
                        context
                            .getPrimaryCache()
                            .byIndex(
                                CONFIG_MAP_INDEX,
                                indexKey(
                                    cm.getMetadata().getName(), cm.getMetadata().getNamespace()))
                            .stream()
                            .map(ResourceID::fromResource)
                            .collect(Collectors.toSet()))
                .build(),
            context);

    return List.of(es);
  }

  private String indexKey(String configMapName, String namespace) {
    return configMapName + "#" + namespace;
  }
}
