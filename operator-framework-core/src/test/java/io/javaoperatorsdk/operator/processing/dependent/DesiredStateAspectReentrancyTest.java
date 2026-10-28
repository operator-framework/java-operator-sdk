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
package io.javaoperatorsdk.operator.processing.dependent;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DefaultContext;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DesiredStateAspect;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class DesiredStateAspectReentrancyTest {

  private static final ConfigMap PRIMARY =
      new ConfigMapBuilder()
          .withNewMetadata()
          .withName("primary")
          .withNamespace("ns")
          .endMetadata()
          .build();

  @Test
  void aspectCanLookUpActualResourceOfTheDependent() {
    final DesiredStateAspect aspect =
        (desired, dependentResource, ctx) ->
            ((DependentResource) dependentResource)
                .getSecondaryResource(ctx.getPrimaryResource(), ctx);
    final var context = contextWith(List.of(aspect));

    final var dependent = new ChildConfigMapDependent();

    assertThatCode(() -> dependent.getSecondaryResource(PRIMARY, context))
        .doesNotThrowAnyException();
  }

  /**
   * The desired state of the other dependent is asked for while the one of the first is still being
   * computed. Dependents that land in the same bin of the map holding the desired states used to
   * fail with "Recursive update".
   */
  @Test
  void aspectCanLookUpActualResourceOfAnotherDependent() {
    final var other = new ChildConfigMapDependent();
    final var dependent = new ChildConfigMapDependent();
    final DesiredStateAspect aspect =
        (desired, dependentResource, ctx) -> {
          if (dependentResource == dependent) {
            ((DependentResource) other).getSecondaryResource(ctx.getPrimaryResource(), ctx);
          }
        };
    final var context = contextWith(List.of(aspect));

    assertThatCode(() -> dependent.getSecondaryResource(PRIMARY, context))
        .doesNotThrowAnyException();
  }

  @Test
  void desiredStateIsComputedOnce() {
    final var context = contextWith(List.of());
    final var dependent = new ChildConfigMapDependent();

    final var first = dependent.getOrComputeDesired(context);

    assertThat(dependent.getOrComputeDesired(context)).isSameAs(first);
  }

  private static DefaultContext<ConfigMap> contextWith(List<DesiredStateAspect> aspects) {
    final ConfigurationService configurationService = mock();
    when(configurationService.desiredStateAspects()).thenReturn(aspects);
    final ControllerConfiguration<ConfigMap> controllerConfiguration = mock();
    when(controllerConfiguration.getConfigurationService()).thenReturn(configurationService);
    final Controller<ConfigMap> controller = mock();
    when(controller.getConfiguration()).thenReturn(controllerConfiguration);
    final EventSourceManager<ConfigMap> esm = mock();
    final EventSource<ConfigMap, ConfigMap> es = mock();
    when(es.getSecondaryResources(any())).thenReturn(Set.of(configMap("actual")));
    when(esm.getEventSourcesFor(ConfigMap.class)).thenReturn(List.of(es));
    when(controller.getEventSourceManager()).thenReturn(esm);
    return new DefaultContext<>(null, controller, PRIMARY, false, false);
  }

  private static ConfigMap configMap(String value) {
    return new ConfigMapBuilder()
        .withNewMetadata()
        .withName("child")
        .withNamespace("ns")
        .endMetadata()
        .withData(Map.of("key", value))
        .build();
  }

  private static class ChildConfigMapDependent
      extends KubernetesDependentResource<ConfigMap, ConfigMap> {
    ChildConfigMapDependent() {
      super(ConfigMap.class);
    }

    @Override
    public int hashCode() {
      return 1;
    }

    @Override
    protected ConfigMap desired(ConfigMap primary, Context<ConfigMap> context) {
      return configMap("desired");
    }

    @Override
    public ConfigMap getOrComputeDesired(Context<ConfigMap> context) {
      return super.getOrComputeDesired(context);
    }
  }
}
