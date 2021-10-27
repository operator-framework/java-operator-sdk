package io.javaoperatorsdk.operator.api;

import java.util.Optional;

/**
 * If the custom resource's status object implements this interface the observed generation will be automatically
 * handled. The last observed generation will be set to status when the status is updated or no update comes from
 * controller. In addition to that will be checked if the controller is generation aware.
 */
public interface ObservedGenerationAware {

    void setObservedGeneration(Long generation);

    Optional<Long> getObservedGeneration();
}
