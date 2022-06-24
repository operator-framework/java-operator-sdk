package io.javaoperatorsdk.operator.sample.primarytosecondary;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryToSecondaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration()
public class JobReconciler
    implements Reconciler<Job>, EventSourceInitializer<Job> {

  private static final String JOB_CLUSTER_INDEX = "job-cluster-index";

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<Job> reconcile(
      Job resource, Context<Job> context) {

    context.getSecondaryResource(Cluster.class)
        .orElseThrow(() -> new IllegalStateException("Secondary resource should be present"));
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<Job> context) {
    context.getPrimaryCache().addIndexer(JOB_CLUSTER_INDEX, (job -> List
        .of(indexKey(job.getSpec().getClusterName(), job.getMetadata().getNamespace()))));

    InformerConfiguration<Cluster> informerConfiguration =
        InformerConfiguration.from(Cluster.class, context)
            .withSecondaryToPrimaryMapper(cluster -> context.getPrimaryCache()
                .byIndex(JOB_CLUSTER_INDEX, indexKey(cluster.getMetadata().getName(),
                    cluster.getMetadata().getNamespace()))
                .stream().map(ResourceID::fromResource).collect(Collectors.toSet()))
            .withPrimaryToSecondaryMapper(
                (PrimaryToSecondaryMapper<Job>) primary -> Set.of(new ResourceID(
                    primary.getSpec().getClusterName(), primary.getMetadata().getNamespace())))
            .withNamespacesInheritedFromController(context)
            .build();

    return EventSourceInitializer
        .nameEventSources(new InformerEventSource<>(informerConfiguration, context));
  }

  private String indexKey(String clusterName, String namespace) {
    return clusterName + "#" + namespace;
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
