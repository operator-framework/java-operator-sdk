package io.javaoperatorsdk.operator.baseapi.patchresourcewithssa;

import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

@Sample(
    tldr = "Patching Resources with Server-Side Apply (SSA)",
    description =
        """
        Demonstrates how to use Server-Side Apply (SSA) for patching primary resources in \
        Kubernetes. The test verifies that the reconciler can patch resources using SSA, which \
        provides better conflict resolution and field management compared to traditional update \
        approaches, including proper handling of managed fields.
        """)
public class PatchResourceWithSSAIT extends PatchWithSSAITBase {

  @Override
  protected Reconciler<?> reconciler() {
    return new PatchResourceWithSSAReconciler();
  }
}
