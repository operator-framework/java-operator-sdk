package io.javaoperatorsdk.operator.baseapi.primarytosecondary;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryToSecondaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

/**
 * This reconciler used in integration tests to show the cases when PrimaryToSecondaryMapper is
 * needed, and to show the use cases when some mechanisms would not work without that. It's not
 * intended to be a reusable code as it is, rather serves for deeper understanding of the problem.
 */
@ControllerConfiguration()
public class JobReconciler implements Reconciler<Job> {

  private static final String JOB_CLUSTER_INDEX = "job-cluster-index";

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  private final boolean addPrimaryToSecondaryMapper;
  private boolean getResourceDirectlyFromCache = false;
  private volatile boolean errorOccurred;

  public JobReconciler() {
    this(true);
  }

  public JobReconciler(boolean addPrimaryToSecondaryMapper) {
    this.addPrimaryToSecondaryMapper = addPrimaryToSecondaryMapper;
  }

  @Override
  public UpdateControl<Job> reconcile(Job resource, Context<Job> context) {
    Cluster cluster;
    if (!getResourceDirectlyFromCache) {
      // this is only possible when there is primary to secondary mapper
      cluster =
          context
              .getSecondaryResource(Cluster.class)
              .orElseThrow(() -> new IllegalStateException("Secondary resource should be present"));
    } else {
      // reading the resource from cache as alternative, works without primary to secondary mapper
      var informerEventSource =
          (InformerEventSource<Cluster, Job>)
              context.eventSourceRetriever().getEventSourceFor(Cluster.class);
      cluster =
          informerEventSource
              .get(
                  new ResourceID(
                      resource.getSpec().getClusterName(), resource.getMetadata().getNamespace()))
              .orElseThrow(
                  () -> new IllegalStateException("Secondary resource cannot be read from cache"));
    }
    if (resource.getStatus() == null) {
      resource.setStatus(new JobStatus());
    }
    numberOfExecutions.addAndGet(1);
    // copy a value to job status, to we can test triggering
    if (!cluster.getSpec().getClusterValue().equals(resource.getStatus().getValueFromCluster())) {
      resource.getStatus().setValueFromCluster(cluster.getSpec().getClusterValue());
      return UpdateControl.patchStatus(resource);
    } else {
      return UpdateControl.noUpdate();
    }
  }

  @Override
  public List<EventSource<?, Job>> prepareEventSources(EventSourceContext<Job> context) {
    context
        .getPrimaryCache()
        .addIndexer(
            JOB_CLUSTER_INDEX,
            (job ->
                List.of(
                    indexKey(job.getSpec().getClusterName(), job.getMetadata().getNamespace()))));

    InformerEventSourceConfiguration.Builder<Cluster> informerConfiguration =
        InformerEventSourceConfiguration.from(Cluster.class, Job.class)
            .withSecondaryToPrimaryMapper(
                cluster ->
                    context
                        .getPrimaryCache()
                        .byIndex(
                            JOB_CLUSTER_INDEX,
                            indexKey(
                                cluster.getMetadata().getName(),
                                cluster.getMetadata().getNamespace()))
                        .stream()
                        .map(ResourceID::fromResource)
                        .collect(Collectors.toSet()))
            .withNamespacesInheritedFromController();

    if (addPrimaryToSecondaryMapper) {
      informerConfiguration =
          informerConfiguration.withPrimaryToSecondaryMapper(
              (PrimaryToSecondaryMapper<Job>)
                  primary ->
                      Set.of(
                          new ResourceID(
                              primary.getSpec().getClusterName(),
                              primary.getMetadata().getNamespace())));
    }

    return List.of(new InformerEventSource<>(informerConfiguration.build(), context));
  }

  private String indexKey(String clusterName, String namespace) {
    return clusterName + "#" + namespace;
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public ErrorStatusUpdateControl<Job> updateErrorStatus(
      Job resource, Context<Job> context, Exception e) {
    errorOccurred = true;
    return ErrorStatusUpdateControl.noStatusUpdate();
  }

  public boolean isErrorOccurred() {
    return errorOccurred;
  }

  @SuppressWarnings("UnusedReturnValue")
  public JobReconciler setGetResourceDirectlyFromCache(boolean getResourceDirectlyFromCache) {
    this.getResourceDirectlyFromCache = getResourceDirectlyFromCache;
    return this;
  }
}
