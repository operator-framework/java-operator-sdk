package io.javaoperatorsdk.operator.api;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

/**
 * If the custom resource's status implements this interface, the observed generation will be
 * automatically handled. The last observed generation will be updated on status.
 * <p>
 * In order for this automatic handling to work the status object returned by
 * {@link CustomResource#getStatus()} should not be null.
 * <p>
 * The observed generation is updated even when {@link UpdateControl#noUpdate()} or
 * {@link UpdateControl#updateResource(HasMetadata)} is called. Although those results call normally
 * does not result in a status update, there will be a subsequent status update Kubernetes API call
 * in this case.
 *
 * @see ObservedGenerationAwareStatus
 */
public interface ObservedGenerationAware {

  void setObservedGeneration(Long generation);

  Long getObservedGeneration();

}
