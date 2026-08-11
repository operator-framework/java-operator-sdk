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
package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceFactory;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Focused unit test for the {@code detectApiVersionChange} wiring performed by {@link
 * KubernetesDependentConverter}, independent of the shared, process-wide {@link
 * io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfigurationResolver} state
 * that other tests in this module mutate.
 */
class KubernetesDependentConverterTest {

  private final KubernetesDependentConverter<GenericKubernetesResource, ConfigMap> converter =
      new KubernetesDependentConverter<>();

  @Test
  void detectApiVersionChangeDefaultsToFalseWhenAnnotationAbsent() {
    var config =
        converter.configFrom(null, spec(PlainWidgetDependentResource.class), controllerConfig());

    assertThat(config.detectApiVersionChange()).isFalse();
  }

  @Test
  void detectApiVersionChangeDefaultsToFalseWhenNotSetOnAnnotation() {
    var annotation = PlainWidgetDependentResource.class.getAnnotation(KubernetesDependent.class);
    var config =
        converter.configFrom(
            annotation, spec(PlainWidgetDependentResource.class), controllerConfig());

    assertThat(config.detectApiVersionChange()).isFalse();
  }

  @Test
  void detectApiVersionChangeCanBeEnabledViaAnnotation() {
    var annotation =
        ApiVersionAwareWidgetDependentResource.class.getAnnotation(KubernetesDependent.class);
    var config =
        converter.configFrom(
            annotation, spec(ApiVersionAwareWidgetDependentResource.class), controllerConfig());

    assertThat(config.detectApiVersionChange()).isTrue();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static DependentResourceSpec<
          GenericKubernetesResource,
          ConfigMap,
          KubernetesDependentResourceConfig<GenericKubernetesResource>>
      spec(
          Class<? extends KubernetesDependentResource<GenericKubernetesResource, ConfigMap>>
              dependentResourceClass) {
    return new DependentResourceSpec(
        dependentResourceClass, "test", Set.of(), null, null, null, null, null);
  }

  private static ControllerConfiguration<ConfigMap> controllerConfig() {
    ControllerConfiguration<ConfigMap> controllerConfig = mock();
    when(controllerConfig.getName()).thenReturn("test-reconciler");
    ConfigurationService configurationService = mock();
    when(configurationService.dependentResourceFactory())
        .thenReturn(DependentResourceFactory.DEFAULT);
    when(controllerConfig.getConfigurationService()).thenReturn(configurationService);
    return controllerConfig;
  }

  @KubernetesDependent
  static class PlainWidgetDependentResource
      extends KubernetesDependentResource<GenericKubernetesResource, ConfigMap>
      implements GarbageCollected<ConfigMap> {
    public PlainWidgetDependentResource() {
      super(GenericKubernetesResource.class, null);
    }
  }

  @KubernetesDependent(detectApiVersionChange = true)
  static class ApiVersionAwareWidgetDependentResource
      extends KubernetesDependentResource<GenericKubernetesResource, ConfigMap>
      implements GarbageCollected<ConfigMap> {
    public ApiVersionAwareWidgetDependentResource() {
      super(GenericKubernetesResource.class, null);
    }
  }
}
