package io.javaoperatorsdk.operator.api;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

/**
 * If the custom resource's status implements this interface, the observed generation will be
 * automatically handled. The last observed generation will be updated on status when the status is
 * instructed to be updated (see below). In addition to that, controller configuration will be
 * checked if is set to generation aware. If generation aware config is turned off, this interface
 * is ignored.
 *
 * In order to work the status object returned by CustomResource.getStatus() should not be null. In
 * addition to that from the controller that the
 * {@link UpdateControl#updateStatusSubResource(CustomResource)} or
 * {@link UpdateControl#updateCustomResourceAndStatus(CustomResource)} should be returned. The
 * observed generation is not updated in other cases.
 *
 * @see ObservedGenerationAwareStatus
 */
public interface ObservedGenerationAware {

  void setObservedGeneration(Long generation);

  Long getObservedGeneration();

}
