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
package io.javaoperatorsdk.operator.processing.event;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ManagedFieldsEntry;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.MockControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceOperations;
import io.javaoperatorsdk.operator.processing.event.ReconciliationDispatcher.CustomResourceFacade;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResourceStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class CustomResourceFacadeTest {

  private static final String FINALIZER = "test.javaoperatorsdk.io/finalizer";

  @Test
  void ssaStatusPatchAppliesOnlyIdentityResourceVersionAndStatus() {
    var resource = testResourceWithMetadataAndStatus();

    var applied = ssaPatchStatus(resource);

    assertThat(applied).isNotSameAs(resource);
    assertThat(applied.getApiVersion()).isEqualTo(resource.getApiVersion());
    assertThat(applied.getKind()).isEqualTo(resource.getKind());
    assertThat(applied.getMetadata().getName()).isEqualTo(resource.getMetadata().getName());
    assertThat(applied.getMetadata().getNamespace())
        .isEqualTo(resource.getMetadata().getNamespace());
    assertThat(applied.getMetadata().getResourceVersion()).isEqualTo("1");
    assertThat(applied.getStatus()).isEqualTo(resource.getStatus());
    assertThat(applied.getSpec()).isNull();
    assertThat(applied.getMetadata().getLabels()).isNullOrEmpty();
    assertThat(applied.getMetadata().getAnnotations()).isNullOrEmpty();
    assertThat(applied.getMetadata().getFinalizers()).isNullOrEmpty();
    assertThat(applied.getMetadata().getManagedFields()).isNullOrEmpty();
    assertThat(applied.getMetadata().getGeneration()).isNull();
    assertThat(applied.getMetadata().getUid()).isEqualTo("uid-1");
  }

  @Test
  void ssaStatusPatchOmitsResourceVersionWhenNotSet() {
    var resource = testResourceWithMetadataAndStatus();
    resource.getMetadata().setResourceVersion(null);

    var applied = ssaPatchStatus(resource);

    assertThat(applied.getMetadata().getResourceVersion()).isNull();
  }

  @Test
  void ssaStatusPatchAppliesNullStatusWhenResourceHasNone() {
    var resource = testResourceWithMetadataAndStatus();
    resource.setStatus(null);

    var applied = ssaPatchStatus(resource);

    assertThat(applied.getStatus()).isNull();
  }

  @Test
  void ssaStatusPatchLeavesPassedResourceUntouched() {
    var resource = testResourceWithMetadataAndStatus();

    ssaPatchStatus(resource);

    assertThat(resource.getMetadata().getLabels()).containsEntry("app", "test");
    assertThat(resource.getMetadata().getFinalizers()).containsExactly(FINALIZER);
    assertThat(resource.getMetadata().getManagedFields()).hasSize(1);
    assertThat(resource.getSpec()).isNotNull();
  }

  @Test
  void ssaStatusPatchTrimsNonCustomResourcePrimaryToo() {
    var deployment =
        new DeploymentBuilder()
            .withNewMetadata()
            .withName("test")
            .withNamespace("default")
            .withResourceVersion("7")
            .withLabels(Map.of("app", "test"))
            .endMetadata()
            .withNewSpec()
            .withReplicas(3)
            .endSpec()
            .withNewStatus()
            .withReadyReplicas(3)
            .endStatus()
            .build();

    var applied = ssaPatchStatus(deployment);

    assertThat(applied.getApiVersion()).isEqualTo("apps/v1");
    assertThat(applied.getKind()).isEqualTo("Deployment");
    assertThat(applied.getMetadata().getName()).isEqualTo("test");
    assertThat(applied.getMetadata().getNamespace()).isEqualTo("default");
    assertThat(applied.getMetadata().getResourceVersion()).isEqualTo("7");
    assertThat(applied.getMetadata().getLabels()).isNullOrEmpty();
    assertThat(applied.getSpec()).isNull();
    assertThat(applied.getStatus().getReadyReplicas()).isEqualTo(3);
  }

  private static TestCustomResource testResourceWithMetadataAndStatus() {
    var resource = TestUtils.testCustomResource();
    resource.getMetadata().setUid("uid-1");
    resource.getMetadata().setLabels(Map.of("app", "test"));
    resource.getMetadata().setAnnotations(Map.of("note", "value"));
    resource.getMetadata().setFinalizers(List.of(FINALIZER));
    resource.getMetadata().setManagedFields(List.of(new ManagedFieldsEntry()));
    var status = new TestCustomResourceStatus();
    status.setConfigMapStatus("created");
    resource.setStatus(status);
    return resource;
  }

  private static <R extends HasMetadata> R ssaPatchStatus(R resource) {
    var configurationService =
        ConfigurationService.newOverriddenConfigurationService(
            new BaseConfigurationService(),
            overrider -> overrider.withUseSSAToPatchPrimaryResource(true));
    var configuration =
        MockControllerConfiguration.forResource(
            (Class<R>) resource.getClass(), configurationService);
    var facade =
        new CustomResourceFacade<>(configuration, configurationService.getResourceCloner());
    Context<R> context = mock(Context.class);
    ResourceOperations<R> resourceOperations = mock(ResourceOperations.class);
    when(context.resourceOperations()).thenReturn(resourceOperations);

    facade.patchStatus(context, resource, resource);

    ArgumentCaptor<R> captor = ArgumentCaptor.forClass((Class<R>) resource.getClass());
    verify(resourceOperations).serverSideApplyPrimaryStatus(captor.capture(), any());
    return captor.getValue();
  }
}
