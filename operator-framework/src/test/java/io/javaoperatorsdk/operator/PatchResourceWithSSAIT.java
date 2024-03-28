package io.javaoperatorsdk.operator;


import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.sample.patchresourcewithssa.PatchResourceWithSSAReconciler;


public class PatchResourceWithSSAIT extends PatchWithSSAITBase {

  @Override
  protected Reconciler<?> reconciler() {
    return new PatchResourceWithSSAReconciler();
  }

}
