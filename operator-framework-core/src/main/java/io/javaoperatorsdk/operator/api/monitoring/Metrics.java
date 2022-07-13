package io.javaoperatorsdk.operator.api.monitoring;

import java.util.Collections;
import java.util.Map;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * An interface that metrics providers can implement and that the SDK will call at different times
 * of its execution cycle.
 */
public interface Metrics {

  /**
   * The default Metrics provider: a no-operation implementation.
   */
  Metrics NOOP = new Metrics() {};

  /**
   * Called when an event has been accepted by the SDK from an event source, which would result in
   * potentially triggering the associated Reconciler.
   *
   * @param event the event
   * @param metadata metadata associated with the resource being processed
   */
  default void receivedEvent(Event event, Map<String, Object> metadata) {}

  /**
   *
   * @deprecated Use (and implement) {@link #receivedEvent(Event, Map)} instead
   */
  @Deprecated
  default void receivedEvent(Event event) {
    receivedEvent(event, Collections.emptyMap());
  }

  /**
   *
   * @deprecated Use (and implement) {@link #reconcileCustomResource(ResourceID, RetryInfo, Map)}
   *             instead
   */
  @Deprecated
  default void reconcileCustomResource(ResourceID resourceID, RetryInfo retryInfo) {
    reconcileCustomResource(resourceID, retryInfo, Collections.emptyMap());
  }

  /**
   * Called right before a resource is dispatched to the ExecutorService for reconciliation.
   *
   * @param resourceID the {@link ResourceID} associated with the resource
   * @param retryInfo the current retry state information for the reconciliation request
   * @param metadata metadata associated with the resource being processed
   */
  default void reconcileCustomResource(ResourceID resourceID, RetryInfo retryInfo,
      Map<String, Object> metadata) {}

  /**
   *
   * @deprecated Use (and implement) {@link #failedReconciliation(ResourceID, Exception, Map)}
   *             instead
   */
  @Deprecated
  default void failedReconciliation(ResourceID resourceID, Exception exception) {
    failedReconciliation(resourceID, exception, Collections.emptyMap());
  }

  /**
   * Called when a precedent reconciliation for the resource associated with the specified
   * {@link ResourceID} resulted in the provided exception, resulting in a retry of the
   * reconciliation.
   *
   * @param resourceID the {@link ResourceID} associated with the resource being processed
   * @param exception the exception that caused the failed reconciliation resulting in a retry
   * @param metadata metadata associated with the resource being processed
   */
  default void failedReconciliation(ResourceID resourceID, Exception exception,
      Map<String, Object> metadata) {}

  /**
   *
   * @deprecated Use (and implement) {@link #cleanupDoneFor(ResourceID, Map)} instead
   */
  @Deprecated
  default void cleanupDoneFor(ResourceID resourceID) {
    cleanupDoneFor(resourceID, Collections.emptyMap());
  }

  /**
   * Called when the resource associated with the specified {@link ResourceID} has been successfully
   * deleted and the clean-up performed by the associated reconciler is finished.
   *
   * @param resourceID the {@link ResourceID} associated with the resource being processed
   * @param metadata metadata associated with the resource being processed
   */
  default void cleanupDoneFor(ResourceID resourceID, Map<String, Object> metadata) {}

  /**
   *
   * @deprecated Use (and implement) {@link #finishedReconciliation(ResourceID, Map)} instead
   */
  @Deprecated
  default void finishedReconciliation(ResourceID resourceID) {
    finishedReconciliation(resourceID, Collections.emptyMap());
  }

  /**
   * Called when the
   * {@link io.javaoperatorsdk.operator.api.reconciler.Reconciler#reconcile(HasMetadata, Context)}
   * method of the Reconciler associated with the resource associated with the specified
   * {@link ResourceID} has sucessfully finished.
   *
   * @param resourceID the {@link ResourceID} associated with the resource being processed
   * @param metadata metadata associated with the resource being processed
   */
  default void finishedReconciliation(ResourceID resourceID, Map<String, Object> metadata) {}

  /**
   * Encapsulates the information about a controller execution i.e. a call to either
   * {@link io.javaoperatorsdk.operator.api.reconciler.Reconciler#reconcile(HasMetadata, Context)}
   * or {@link io.javaoperatorsdk.operator.api.reconciler.Cleaner#cleanup(HasMetadata, Context)}.
   * Note that instances are automatically created for you by the SDK and passed to your Metrics
   * implementation at the appropriate time to the
   * {@link #timeControllerExecution(ControllerExecution)} method.
   *
   * @param <T> the outcome type associated with the controller execution. Currently, one of
   *        {@link io.javaoperatorsdk.operator.api.reconciler.UpdateControl} or
   *        {@link io.javaoperatorsdk.operator.api.reconciler.DeleteControl}
   */
  interface ControllerExecution<T> {

    /**
     * Retrieves the name of type of reconciliation being performed: either {@code reconcile} or
     * {@code cleanup}.
     *
     * @return the name of type of reconciliation being performed
     */
    String name();

    /**
     * Retrieves the name of the controller executing the reconciliation.
     *
     * @return the associated controller name
     */
    String controllerName();

    /**
     * Retrieves the name of the successful result when the reconciliation ended positively.
     * Possible values comes from the different outcomes provided by
     * {@link io.javaoperatorsdk.operator.api.reconciler.UpdateControl} or
     * {@link io.javaoperatorsdk.operator.api.reconciler.DeleteControl}.
     *
     * @param result the reconciliation result
     * @return a name associated with the specified outcome
     */
    String successTypeName(T result);

    /**
     * Retrieves the {@link ResourceID} of the resource associated with the controller execution
     * being considered
     *
     * @return the {@link ResourceID} of the resource being reconciled
     */
    ResourceID resourceID();

    /**
     * Retrieves metadata associated with the current reconciliation, typically additional
     * information (such as kind) about the resource being reconciled
     *
     * @return metadata associated with the current reconciliation
     */
    Map<String, Object> metadata();

    /**
     * Performs the controller execution.
     *
     * @return the result of the controller execution
     * @throws Exception if an error occurred during the controller's execution
     */
    T execute() throws Exception;
  }

  /**
   * Times the execution of the controller operation encapsulated by the provided
   * {@link ControllerExecution}.
   *
   * @param execution the controller operation to be timed
   * @return the result of the controller's execution if successful
   * @param <T> the type of the outcome/result of the controller's execution
   * @throws Exception if an error occurred during the controller's execution, usually this should
   *         just be a pass-through of whatever the controller returned
   */
  default <T> T timeControllerExecution(ControllerExecution<T> execution) throws Exception {
    return execution.execute();
  }

  /**
   * Monitors the size of the specified map. This currently isn't used directly by the SDK but could
   * be used by operators to monitor some of their structures, such as cache size.
   *
   * @param map the Map which size is to be monitored
   * @param name the name of the provided Map to be used in metrics data
   * @return the Map that was passed in so the registration can be done as part of an assignment
   *         statement.
   * @param <T> the type of the Map being monitored
   */
  @SuppressWarnings("unused")
  default <T extends Map<?, ?>> T monitorSizeOf(T map, String name) {
    return map;
  }
}
