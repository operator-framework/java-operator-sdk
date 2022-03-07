package io.javaoperatorsdk.operator.processing.dependent.waitfor;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.BaseControl;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

import static io.javaoperatorsdk.operator.api.reconciler.UpdateControl.*;

public class Waiters {

  // todo
  public static <R, P extends HasMetadata> Waiter<R, P> noWaitNoUpdateWaiter() {
    return (Waiter<R, P>) new DefaultWaiter<>()
        .setConditionNotMetHandler(new ConditionNotFulfilledHandler() {
          @Override
          public <T extends BaseControl<T>> T createControl() {
            return (T) UpdateControl.noUpdate();
          }
        });
  }

}
