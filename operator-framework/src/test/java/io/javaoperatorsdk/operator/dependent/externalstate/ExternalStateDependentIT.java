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
package io.javaoperatorsdk.operator.dependent.externalstate;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

@Sample(
    tldr = "External State Tracking in Dependent Resources",
    description =
        """
        Demonstrates managing dependent resources with external state that needs to be tracked \
        independently of Kubernetes resources. This pattern allows operators to maintain state \
        information for external systems or resources, ensuring proper reconciliation even when \
        the external state differs from the desired Kubernetes resource state.
        """)
public class ExternalStateDependentIT extends ExternalStateTestBase {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(ExternalStateDependentReconciler.class)
          .build();

  @Override
  public LocallyRunOperatorExtension extension() {
    return operator;
  }
}
