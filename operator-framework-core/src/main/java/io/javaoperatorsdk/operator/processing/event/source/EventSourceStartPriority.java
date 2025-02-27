package io.javaoperatorsdk.operator.processing.event.source;

/**
 * Defines priority levels for {@link EventSource} implementation to ensure that some sources are
 * started before others
 */
public enum EventSourceStartPriority {

  /**
   * Event Sources with this priority are started and synced before the event source with DEFAULT
   * priority. This is needed if the event source holds information about another resource's state.
   * In this situation, it is needed to initialize this event source before the one associated with
   * resources which state is being tracked since that state information might be required to
   * properly retrieve the other resources.
   *
   * <p>For example a {@code ConfigMap} could store the identifier of a fictional external resource
   * {@code A}. In this case, the event source tracking {@code A} resources might need the
   * identifier from the {@code ConfigMap} to identify and check the state of {@code A} resources.
   * This is usually needed before any reconciliation occurs and the only way to ensure the proper
   * behavior in this case is to make sure that the event source tracking the {@code ConfigMaps} (in
   * this example) is started/cache-synced before the event source for {@code A} resources gets
   * started.
   */
  RESOURCE_STATE_LOADER,
  DEFAULT
}
