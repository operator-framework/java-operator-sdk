package io.javaoperatorsdk.operator.api.reconciler;

import java.util.*;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

public class EventSourceUtils {

  @SuppressWarnings("unchecked")
  public static <P extends HasMetadata> List<EventSource<?, P>> dependentEventSources(
      EventSourceContext<P> eventSourceContext, DependentResource... dependentResources) {
    return Arrays.stream(dependentResources)
        .flatMap(dr -> dr.eventSource(eventSourceContext).stream())
        .toList();
  }

  @SuppressWarnings("unchecked")
  public static <P extends HasMetadata> List<EventSource<?, P>> eventSourcesFromWorkflow(
      EventSourceContext<P> context, Workflow<P> workflow) {
    return workflow.getDependentResourcesWithoutActivationCondition().stream()
        .flatMap(dr -> dr.eventSource(context).stream())
        .toList();
  }
}
