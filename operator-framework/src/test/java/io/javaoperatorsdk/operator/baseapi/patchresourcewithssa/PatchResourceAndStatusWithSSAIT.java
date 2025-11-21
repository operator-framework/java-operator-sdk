package io.javaoperatorsdk.operator.baseapi.patchresourcewithssa;

import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

@Sample(
    tldr = "Patching resource and status with Server-Side Apply",
    description =
        """
        Demonstrates how to use Server-Side Apply (SSA) to patch both the primary resource and its \
        status subresource. SSA provides better conflict resolution and field management \
        tracking compared to traditional merge patches, making it the recommended approach \
        for resource updates.
        """)
public class PatchResourceAndStatusWithSSAIT extends PatchWithSSAITBase {

  @Override
  protected Reconciler<?> reconciler() {
    return new PatchResourceAndStatusWithSSAReconciler();
  }
}
