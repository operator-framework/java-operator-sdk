package io.javaoperatorsdk.operator.processing.event.source;

public enum EventSourceStartPriority {

  /**
   * Event Sources with this priority are started and synced before the event source with DEFAULT
   * priority. The use case to use this, if the event source holds an information regarding the
   * state of a resource. For example a ConfigMap would store an ID of an external resource, in this
   * case an event source that tracks the external resource might need this ID (event before the
   * reconciliation) to check the state of the external resource. The only way to ensure that the ID
   * is already cached is to start/sync related event source before the event source of the external
   * resource.
   */
  RESOURCE_STATE_LOADER, DEFAULT


}
