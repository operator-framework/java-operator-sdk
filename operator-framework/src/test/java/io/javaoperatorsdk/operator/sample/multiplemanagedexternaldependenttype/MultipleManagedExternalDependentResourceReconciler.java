package io.javaoperatorsdk.operator.sample.multiplemanagedexternaldependenttype;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PollingEventSource;
import io.javaoperatorsdk.operator.support.ExternalResource;
import io.javaoperatorsdk.operator.support.ExternalServiceMock;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

import static io.javaoperatorsdk.operator.sample.multiplemanagedexternaldependenttype.MultipleManagedExternalDependentResourceReconciler.CONFIG_MAP_EVENT_SOURCE;

@ControllerConfiguration(dependents = {
    @Dependent(type = ExternalDependentResource1.class,
        useEventSourceWithName = CONFIG_MAP_EVENT_SOURCE),
    @Dependent(type = ExternalDependentResource2.class,
        useEventSourceWithName = CONFIG_MAP_EVENT_SOURCE)
})
public class MultipleManagedExternalDependentResourceReconciler
    implements Reconciler<MultipleManagedExternalDependentResourceCustomResource>,
    TestExecutionInfoProvider,
    EventSourceInitializer<MultipleManagedExternalDependentResourceCustomResource> {

  public static final String CONFIG_MAP_EVENT_SOURCE = "ConfigMapEventSource";
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
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<MultipleManagedExternalDependentResourceCustomResource> context) {

    PollingEventSource<ExternalResource, MultipleManagedExternalDependentResourceCustomResource> pollingEventSource =
        new PollingEventSource<>(() -> {
          var lists = externalServiceMock.listResources();
          Map<ResourceID, Set<ExternalResource>> res = new HashMap<>();
          lists.forEach(er -> {
            var resourceId = er.toResourceID();
            res.computeIfAbsent(resourceId, rid -> new HashSet<>());
            res.get(resourceId).add(er);
          });
          return res;
        }, 1000L, ExternalResource.class, ExternalResource::getId);

    return Map.of(CONFIG_MAP_EVENT_SOURCE, pollingEventSource);
  }
}
