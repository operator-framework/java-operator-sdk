package io.javaoperatorsdk.operator.api;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

/**
 * If the custom resource's status implements this interface, the observed generation will be
 * automatically handled. The last observed generation will be updated on status.
 * <p>
 * In order for this automatic handling to work the status object returned by
 * {@link CustomResource#getStatus()} should not be null.
 * <p>
 * The observed generation is updated with SSA mode only if
 * {@link UpdateControl#patchStatus(HasMetadata)} or
 * {@link UpdateControl#patchResourceAndStatus(HasMetadata)} is called. In non-SSA mode (see
 * {@link ConfigurationService#useSSAToPatchPrimaryResource()}) observed generation is update even
 * if patch is not called.
 *
 * @see ObservedGenerationAwareStatus
 */
public interface ObservedGenerationAware {

  void setObservedGeneration(Long generation);

  Long getObservedGeneration();

}
