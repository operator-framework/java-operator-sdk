package io.javaoperatorsdk.operator.sample.primarytosecondaydependent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryToSecondaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

import static io.javaoperatorsdk.operator.sample.primarytosecondaydependent.PrimaryToSecondaryDependentReconciler.CONFIG_MAP;
import static io.javaoperatorsdk.operator.sample.primarytosecondaydependent.PrimaryToSecondaryDependentReconciler.CONFIG_MAP_EVENT_SOURCE;

/**
 * Sample showcases how it is possible to do a primary to secondary mapper for a dependent resource.
 * Note that this is usually just used with read only resources. So it has limited usage, one reason
 * to use it is to have nice condition on that resource within a workflow.
 */
@ControllerConfiguration(dependents = {@Dependent(type = ConfigMapDependent.class,
    name = CONFIG_MAP,
    reconcilePrecondition = ConfigMapReconcilePrecondition.class,
    useEventSourceWithName = CONFIG_MAP_EVENT_SOURCE),
    @Dependent(type = SecretDependent.class, dependsOn = CONFIG_MAP)})
public class PrimaryToSecondaryDependentReconciler
    implements Reconciler<PrimaryToSecondaryDependentCustomResource>, TestExecutionInfoProvider,
    EventSourceInitializer<PrimaryToSecondaryDependentCustomResource> {

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

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<PrimaryToSecondaryDependentCustomResource> context) {
    context.getPrimaryCache().addIndexer(CONFIG_MAP_INDEX, (primary -> List
        .of(indexKey(primary.getSpec().getConfigMapName(), primary.getMetadata().getNamespace()))));

    var cmES = new InformerEventSource<>(InformerConfiguration
        .from(ConfigMap.class, context)
        .withPrimaryToSecondaryMapper(
            (PrimaryToSecondaryMapper<PrimaryToSecondaryDependentCustomResource>) p -> Set
                .of(new ResourceID(p.getSpec().getConfigMapName(), p.getMetadata().getNamespace())))
        .withSecondaryToPrimaryMapper(cm -> context.getPrimaryCache()
            .byIndex(CONFIG_MAP_INDEX, indexKey(cm.getMetadata().getName(),
                cm.getMetadata().getNamespace()))
            .stream().map(ResourceID::fromResource).collect(Collectors.toSet()))
        .build(),
        context);

    return Map.of(CONFIG_MAP_EVENT_SOURCE, cmES);
  }

  private String indexKey(String clusterName, String namespace) {
    return clusterName + "#" + namespace;
  }
}
