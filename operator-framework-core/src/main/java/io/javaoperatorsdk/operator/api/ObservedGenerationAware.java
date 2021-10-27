package io.javaoperatorsdk.operator.api;

import java.util.Optional;

import io.fabric8.kubernetes.client.CustomResource;

/**
 * If the custom resource's status object implements this interface the observed generation will be
 * automatically handled. The last observed generation will be set to status when the status is
 * updated or no update comes from controller. In addition to that will be checked if the controller
 * is generation aware.
 *
 * In order to work the status object returned by CustomResource.getStatus() should not be null. In
 * addition to that from the controller that the
 * {@link UpdateControl#updateStatusSubResource(CustomResource)} or
 * {@link UpdateControl#updateCustomResourceAndStatus(CustomResource)} should be returned. The
 * observed generation is not updated in other cases.
 *
 */
public interface ObservedGenerationAware {

  void setObservedGeneration(Long generation);

  Optional<Long> getObservedGeneration();

}
