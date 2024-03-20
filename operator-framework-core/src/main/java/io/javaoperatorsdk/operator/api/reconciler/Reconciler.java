package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import java.util.*;

public interface Reconciler<P extends HasMetadata> {

  /**
   * The implementation of this operation is required to be idempotent. Always use the UpdateControl
   * object to make updates on custom resource if possible.
   *
   * @throws Exception from the custom implementation
   * @param resource the resource that has been created or updated
   * @param context the context with which the operation is executed
   * @return UpdateControl to manage updates on the custom resource (usually the status) after
   *         reconciliation.
   */
  UpdateControl<P> reconcile(P resource, Context<P> context) throws Exception;


  /**
   * Prepares a map of {@link EventSource} implementations keyed by the name with which they need to
   * be registered by the SDK.
   *
   * @param context a {@link EventSourceContext} providing access to information useful to event
   *        sources
   * @return a map of event sources to register
   */
  default Map<String, EventSource> prepareEventSources(EventSourceContext<P> context) {
    return Map.of();
  }

}
