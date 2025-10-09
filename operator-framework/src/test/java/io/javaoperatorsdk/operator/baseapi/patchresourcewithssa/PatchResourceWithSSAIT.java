/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
