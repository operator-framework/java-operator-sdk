package io.javaoperatorsdk.operator.sample.multiplemanagedexternaldependenttype;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PollingConfigurationBuilder;
import io.javaoperatorsdk.operator.processing.event.source.polling.PollingEventSource;
import io.javaoperatorsdk.operator.support.ExternalResource;
import io.javaoperatorsdk.operator.support.ExternalServiceMock;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

import static io.javaoperatorsdk.operator.sample.multiplemanagedexternaldependenttype.MultipleManagedExternalDependentResourceReconciler.EVENT_SOURCE_NAME;

@Workflow(dependents = {
    @Dependent(type = ExternalDependentResource1.class,
        useEventSourceWithName = EVENT_SOURCE_NAME),
    @Dependent(type = ExternalDependentResource2.class,
        useEventSourceWithName = EVENT_SOURCE_NAME)
})
@ControllerConfiguration()
public class MultipleManagedExternalDependentResourceReconciler
    implements Reconciler<MultipleManagedExternalDependentResourceCustomResource>,
    TestExecutionInfoProvider {

  public static final String EVENT_SOURCE_NAME = "ConfigMapEventSource";
  protected ExternalServiceMock externalServiceMock = ExternalServiceMock.getInstance();
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  public MultipleManagedExternalDependentResourceReconciler() {}

  @Override
  public UpdateControl<MultipleManagedExternalDependentResourceCustomResource> reconcile(
      MultipleManagedExternalDependentResourceCustomResource resource,
      Context<MultipleManagedExternalDependentResourceCustomResource> context) {
    numberOfExecutions.getAndIncrement();

    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public List<EventSource> prepareEventSources(
      EventSourceContext<MultipleManagedExternalDependentResourceCustomResource> context) {

    final PollingEventSource.GenericResourceFetcher<ExternalResource> fetcher = () -> {
      var lists = externalServiceMock.listResources();
      final Map<ResourceID, Set<ExternalResource>> res = new HashMap<>();
      lists.forEach(er -> {
        var resourceId = er.toResourceID();
        res.computeIfAbsent(resourceId, rid -> new HashSet<>());
        res.get(resourceId).add(er);
      });
      return res;
    };

    PollingEventSource<ExternalResource, MultipleManagedExternalDependentResourceCustomResource> pollingEventSource =
        new PollingEventSource<>(ExternalResource.class,
            new PollingConfigurationBuilder<>(fetcher, Duration.ofMillis(1000L))
                .withName(EVENT_SOURCE_NAME)
                .withCacheKeyMapper(ExternalResource::getId)
                .build());

    return List.of(pollingEventSource);
  }
}
