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
package io.javaoperatorsdk.operator.processing.matcher;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatusBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DefaultContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StatusMatchersTest {

  private static final Context<?> context = new TestContext();

  private final UpdateStatusMatcher updateStatusMatcher = UpdateStatusMatcher.getInstance();
  private final SSAStatusMatcher ssaStatusMatcher = SSAStatusMatcher.getInstance();

  @Test
  void matchesEqualStatus() {
    var desired = deploymentWithStatus(1);
    var actual = deploymentWithStatus(1);
    assertThat(updateStatusMatcher.matches(desired, actual, context)).isTrue();
    assertThat(ssaStatusMatcher.matches(desired, actual, context)).isTrue();
  }

  @Test
  void doesNotMatchDifferentStatus() {
    var desired = deploymentWithStatus(1);
    var actual = deploymentWithStatus(2);
    assertThat(updateStatusMatcher.matches(desired, actual, context)).isFalse();
    assertThat(ssaStatusMatcher.matches(desired, actual, context)).isFalse();
  }

  @Test
  void ignoresChangesOutsideStatus() {
    var desired = deploymentWithStatus(1);
    desired.getSpec().setReplicas(5);
    var actual = deploymentWithStatus(1);
    assertThat(updateStatusMatcher.matches(desired, actual, context)).isTrue();
    assertThat(ssaStatusMatcher.matches(desired, actual, context)).isTrue();
  }

  private static Deployment deploymentWithStatus(int statusReplicas) {
    return new DeploymentBuilder()
        .withNewMetadata()
        .withName("test")
        .withNamespace("default")
        .endMetadata()
        .withNewSpec()
        .withReplicas(1)
        .endSpec()
        .withStatus(new DeploymentStatusBuilder().withReplicas(statusReplicas).build())
        .build();
  }

  private static class TestContext extends DefaultContext<HasMetadata> {
    private final KubernetesClient client = MockKubernetesClient.client(HasMetadata.class);

    TestContext() {
      super(mock(), mock(), null, false, false);
    }

    @Override
    public KubernetesClient getClient() {
      return client;
    }
  }
}
