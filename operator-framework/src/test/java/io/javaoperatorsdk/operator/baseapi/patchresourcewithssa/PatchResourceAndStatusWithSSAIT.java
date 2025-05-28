package io.javaoperatorsdk.operator.baseapi.patchresourcewithssa;

import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

public class PatchResourceAndStatusWithSSAIT extends PatchWithSSAITBase {

  @Override
  protected Reconciler<?> reconciler() {
    return new PatchResourceAndStatusWithSSAReconciler();
  }
}
