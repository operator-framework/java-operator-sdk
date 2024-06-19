package io.javaoperatorsdk.operator;

import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.sample.patchresourcewithssa.PatchResourceAndStatusWithSSAReconciler;

public class PatchResourceAndStatusWithSSAIT extends PatchWithSSAITBase {

  @Override
  protected Reconciler<?> reconciler() {
    return new PatchResourceAndStatusWithSSAReconciler();
  }

}
