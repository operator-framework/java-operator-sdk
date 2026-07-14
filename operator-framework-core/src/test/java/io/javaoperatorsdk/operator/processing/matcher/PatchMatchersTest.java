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
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DefaultContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PatchMatchersTest {

  private static final Context<?> context = new TestContext();

  private final JsonPatchMatcher jsonPatchMatcher = JsonPatchMatcher.getInstance();
  private final JsonMergePatchMatcher mergePatchMatcher = JsonMergePatchMatcher.getInstance();
  private final JsonPatchStatusMatcher jsonPatchStatusMatcher =
      JsonPatchStatusMatcher.getInstance();
  private final JsonMergePatchStatusMatcher mergePatchStatusMatcher =
      JsonMergePatchStatusMatcher.getInstance();

  // ---- JSON Patch (whole resource) ----

  @Test
  void jsonPatchMatchesIdenticalResources() {
    assertThat(jsonPatchMatcher.matches(deployment(), deployment(), context)).isTrue();
  }

  @Test
  void jsonPatchDoesNotMatchWhenActualHasAdditionalField() {
    var actual = deployment();
    actual.getMetadata().getLabels().put("extra", "value");
    assertThat(jsonPatchMatcher.matches(deployment(), actual, context)).isFalse();
  }

  @Test
  void jsonPatchDoesNotMatchWhenDesiredHasAdditionalField() {
    var desired = deployment();
    desired.getMetadata().getLabels().put("extra", "value");
    assertThat(jsonPatchMatcher.matches(desired, deployment(), context)).isFalse();
  }

  @Test
  void jsonPatchDoesNotMatchOnDifferentValue() {
    var desired = deployment();
    desired.getSpec().setReplicas(5);
    assertThat(jsonPatchMatcher.matches(desired, deployment(), context)).isFalse();
  }

  // ---- JSON Merge Patch (whole resource) ----

  @Test
  void mergePatchMatchesIdenticalResources() {
    assertThat(mergePatchMatcher.matches(deployment(), deployment(), context)).isTrue();
  }

  @Test
  void mergePatchMatchesWhenActualHasAdditionalField() {
    // a merge patch of the desired state leaves fields only present in the actual state untouched
    var actual = deployment();
    actual.getMetadata().getLabels().put("extra", "value");
    assertThat(mergePatchMatcher.matches(deployment(), actual, context)).isTrue();
  }

  @Test
  void mergePatchDoesNotMatchWhenDesiredHasAdditionalField() {
    var desired = deployment();
    desired.getMetadata().getLabels().put("extra", "value");
    assertThat(mergePatchMatcher.matches(desired, deployment(), context)).isFalse();
  }

  @Test
  void mergePatchDoesNotMatchOnDifferentValue() {
    var desired = deployment();
    desired.getSpec().setReplicas(5);
    assertThat(mergePatchMatcher.matches(desired, deployment(), context)).isFalse();
  }

  // ---- Status matchers ----

  @Test
  void statusMatchersIgnoreChangesOutsideStatus() {
    var desired = deploymentWithStatus();
    desired.getSpec().setReplicas(5);
    var actual = deploymentWithStatus();
    assertThat(jsonPatchStatusMatcher.matches(desired, actual, context)).isTrue();
    assertThat(mergePatchStatusMatcher.matches(desired, actual, context)).isTrue();
  }

  @Test
  void statusMatchersDoNotMatchOnDifferentStatus() {
    var desired = deploymentWithStatus();
    desired.getStatus().setReplicas(9);
    var actual = deploymentWithStatus();
    assertThat(jsonPatchStatusMatcher.matches(desired, actual, context)).isFalse();
    assertThat(mergePatchStatusMatcher.matches(desired, actual, context)).isFalse();
  }

  @Test
  void statusMatchersDifferOnAdditionalActualStatusField() {
    var actual = deploymentWithStatus();
    actual.getStatus().setAvailableReplicas(1);
    var desired = deploymentWithStatus();

    // json patch sees the extra actual field as a removal, so it does not match
    assertThat(jsonPatchStatusMatcher.matches(desired, actual, context)).isFalse();
    // merge patch tolerates fields only present in the actual state
    assertThat(mergePatchStatusMatcher.matches(desired, actual, context)).isTrue();
  }

  @Test
  void statusMatchersMatchWhenBothStatusesAbsent() {
    assertThat(jsonPatchStatusMatcher.matches(deployment(), deployment(), context)).isTrue();
    assertThat(mergePatchStatusMatcher.matches(deployment(), deployment(), context)).isTrue();
  }

  private static Deployment deployment() {
    return new DeploymentBuilder()
        .withNewMetadata()
        .withName("test")
        .withNamespace("default")
        .addToLabels("app", "test")
        .endMetadata()
        .withNewSpec()
        .withReplicas(1)
        .endSpec()
        .build();
  }

  private static Deployment deploymentWithStatus() {
    var deployment = deployment();
    deployment.setStatus(new io.fabric8.kubernetes.api.model.apps.DeploymentStatus());
    deployment.getStatus().setReplicas(1);
    return deployment;
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
