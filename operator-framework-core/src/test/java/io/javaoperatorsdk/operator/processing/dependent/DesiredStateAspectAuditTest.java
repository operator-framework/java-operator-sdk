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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import io.javaoperatorsdk.operator.processing.dependent.Matcher.Result;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Audit repro tests for the DesiredStateAspect feature (commit 110199c47). */
@SuppressWarnings({"unchecked", "rawtypes"})
class DesiredStateAspectAuditTest {

  private static final ConfigMap PRIMARY =
      new ConfigMapBuilder()
          .withNewMetadata()
          .withName("primary")
          .withNamespace("ns")
          .endMetadata()
          .build();

  private static Controller<ConfigMap> controllerWith(List<DesiredStateAspect> aspects) {
    final ConfigurationService configurationService = mock();
    when(configurationService.desiredStateAspects()).thenReturn(aspects);
    final ControllerConfiguration<ConfigMap> controllerConfiguration = mock();
    when(controllerConfiguration.getConfigurationService()).thenReturn(configurationService);
    final Controller<ConfigMap> controller = mock();
    when(controller.getConfiguration()).thenReturn(controllerConfiguration);
    return controller;
  }

  /**
   * The aspect contract says it receives "the DependentResource the desired state was computed
   * for". For bulk dependents, it instead receives the private per-item wrapper
   * BulkDependentResourceReconciler.BulkDependentResourceInstance, so aspects keyed on the
   * dependent (type checks, name(), getClass().getSimpleName() as in DesiredStateAspectIT)
   * misbehave.
   */
  @Test
  void bulkDependentAspectReceivesTheUserBulkDependentResource() {
    final List<DependentResource<?, ?>> seen = new ArrayList<>();
    final DesiredStateAspect aspect =
        (desired, dependentResource, ctx) -> seen.add(dependentResource);
    final var context =
        new DefaultContext<>(null, controllerWith(List.of(aspect)), PRIMARY, false, false);

    final var bulk = new CreatingBulk();
    bulk.reconcile(PRIMARY, context);

    assertThat(seen).hasSize(1);
    assertThat(seen.get(0).name()).isEqualTo(bulk.name());
    assertThat(seen.get(0)).isSameAs(bulk);
  }

  /**
   * Aspects run inside ConcurrentHashMap.computeIfAbsent. An aspect that looks up the actual
   * resource of the dependent it is given (e.g. to preserve an existing label) re-enters
   * getOrComputeDesired for the same key through KubernetesDependentResource
   * .selectTargetSecondaryResource -> targetSecondaryResourceID, and CHM throws "Recursive update".
   */
  @Test
  void aspectCanLookUpActualResourceOfTheDependent() {
    final DesiredStateAspect aspect =
        (desired, dependentResource, ctx) ->
            ((DependentResource) dependentResource)
                .getSecondaryResource(ctx.getPrimaryResource(), ctx);
    final var controller = controllerWith(List.of(aspect));
    final EventSourceManager<ConfigMap> esm = mock();
    final EventSource<ConfigMap, ConfigMap> es = mock();
    when(es.getSecondaryResources(any())).thenReturn(Set.of(configMap("child", "actual")));
    when(esm.getEventSourcesFor(ConfigMap.class)).thenReturn(List.of(es));
    when(controller.getEventSourceManager()).thenReturn(esm);
    final var context = new DefaultContext<>(null, controller, PRIMARY, false, false);

    final var dependent = new ChildConfigMapDependent();

    assertThatCode(() -> dependent.getSecondaryResource(PRIMARY, context))
        .doesNotThrowAnyException();
  }

  /**
   * Before 110199c47, a DefaultContext built on a Controller whose getConfiguration() is not
   * stubbed (the pattern the existing tests used: new DefaultContext<>(mock(), mock(), ...)) could
   * compute desired states. Now applyDesiredStateAspects dereferences the null
   * ControllerConfiguration for every HasMetadata desired state.
   */
  @Test
  void desiredComputationDoesNotRequireAspectConfiguration() {
    final var context = new DefaultContext<>(null, mock(Controller.class), PRIMARY, false, false);
    final var dependent = new ChildConfigMapDependent();

    assertThatCode(() -> dependent.getOrComputeDesired(context)).doesNotThrowAnyException();
  }

  private static ConfigMap configMap(String name, String value) {
    return new ConfigMapBuilder()
        .withNewMetadata()
        .withName(name)
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
    protected ConfigMap desired(ConfigMap primary, Context<ConfigMap> context) {
      return configMap("child", "desired");
    }

    @Override
    public ConfigMap getOrComputeDesired(Context<ConfigMap> context) {
      return super.getOrComputeDesired(context);
    }
  }

  private static class CreatingBulk extends AbstractDependentResource<ConfigMap, ConfigMap>
      implements BulkDependentResource<ConfigMap, ConfigMap, String>,
          Creator<ConfigMap, ConfigMap> {

    CreatingBulk() {
      super("my-bulk");
    }

    @Override
    public Map<String, ConfigMap> desiredResources(ConfigMap primary, Context<ConfigMap> context) {
      var desired = new LinkedHashMap<String, ConfigMap>();
      desired.put("a", configMap("a", "desired"));
      return desired;
    }

    @Override
    public Map<String, ConfigMap> getSecondaryResources(
        ConfigMap primary, Context<ConfigMap> context) {
      return Map.of();
    }

    @Override
    public ConfigMap create(ConfigMap desired, ConfigMap primary, Context<ConfigMap> context) {
      return desired;
    }

    @Override
    public void deleteTargetResource(
        ConfigMap primary, ConfigMap resource, String key, Context<ConfigMap> context) {}

    @Override
    public Result<ConfigMap> match(
        ConfigMap actualResource,
        ConfigMap desired,
        ConfigMap primary,
        Context<ConfigMap> context) {
      return Result.computed(false, desired);
    }

    @Override
    public Result<ConfigMap> match(
        ConfigMap resource, ConfigMap primary, Context<ConfigMap> context) {
      return Result.computed(false, resource);
    }

    @Override
    protected Optional<ConfigMap> selectTargetSecondaryResource(
        Set<ConfigMap> secondaryResources, ConfigMap primary, Context<ConfigMap> context) {
      return Optional.empty();
    }

    @Override
    protected void onCreated(ConfigMap primary, ConfigMap created, Context<ConfigMap> context) {}

    @Override
    protected void onUpdated(
        ConfigMap primary, ConfigMap updated, ConfigMap actual, Context<ConfigMap> context) {}

    @Override
    public Class<ConfigMap> resourceType() {
      return ConfigMap.class;
    }
  }
}
