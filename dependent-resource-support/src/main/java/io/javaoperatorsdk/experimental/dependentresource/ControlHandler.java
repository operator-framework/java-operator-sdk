package io.javaoperatorsdk.experimental.dependentresource;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

public interface ControlHandler<P extends HasMetadata> {

  UpdateControl<P> updateControl(ReconciliationContext<P> reconciliationContext);

  default DeleteControl deleteControl(ReconciliationContext<P> reconciliationContext) {
    return DeleteControl.defaultDelete();
  }
}
