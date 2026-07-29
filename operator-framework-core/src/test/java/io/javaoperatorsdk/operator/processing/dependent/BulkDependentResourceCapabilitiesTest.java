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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult.Operation;
import io.javaoperatorsdk.operator.processing.dependent.Matcher.Result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * A {@link BulkDependentResource} does not have to implement every one of {@link Creator}, {@link
 * Updater} and {@link Deleter}. The capabilities of the bulk resource itself must therefore be
 * honoured, not those of the internal per-item wrapper (which implements all three).
 */
@SuppressWarnings("unchecked")
class BulkDependentResourceCapabilitiesTest {

  private static final ConfigMap PRIMARY =
      new ConfigMapBuilder().withNewMetadata().withName("primary").endMetadata().build();

  @Test
  void doesNotAttemptUpdateWhenBulkResourceIsNotUpdater() {
    var result = new CreateAndDeleteOnlyBulk().reconcile(PRIMARY, mock(Context.class));

    assertThat(result.getSingleOperation()).isEqualTo(Operation.NONE);
  }

  @Test
  void doesNotAttemptCreateWhenBulkResourceIsNotCreator() {
    var result = new DeleteOnlyBulk().reconcile(PRIMARY, mock(Context.class));

    assertThat(result.getResourceOperations()).isEmpty();
  }

  /** Can be created and deleted, but deliberately is not an {@link Updater}. */
  private static class CreateAndDeleteOnlyBulk extends BaseBulk
      implements Creator<ConfigMap, ConfigMap>, Deleter<ConfigMap> {

    @Override
    public Map<String, ConfigMap> getSecondaryResources(
        ConfigMap primary, Context<ConfigMap> context) {
      // an actual resource exists, but differs from the desired one
      var actual = new LinkedHashMap<String, ConfigMap>();
      actual.put("a", configMap(Map.of("key", "actual")));
      return actual;
    }

    @Override
    public ConfigMap create(ConfigMap desired, ConfigMap primary, Context<ConfigMap> context) {
      throw new AssertionError("create must not be called, the resource already exists");
    }
  }

  /** Can only be deleted: neither {@link Creator} nor {@link Updater}. */
  private static class DeleteOnlyBulk extends BaseBulk implements Deleter<ConfigMap> {

    @Override
    public Map<String, ConfigMap> getSecondaryResources(
        ConfigMap primary, Context<ConfigMap> context) {
      return Map.of();
    }
  }

  private abstract static class BaseBulk extends AbstractDependentResource<ConfigMap, ConfigMap>
      implements BulkDependentResource<ConfigMap, ConfigMap, String> {

    @Override
    public Map<String, ConfigMap> desiredResources(ConfigMap primary, Context<ConfigMap> context) {
      var desired = new LinkedHashMap<String, ConfigMap>();
      desired.put("a", configMap(Map.of("key", "desired")));
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

  private static ConfigMap configMap(Map<String, String> data) {
    return new ConfigMapBuilder()
        .withNewMetadata()
        .withName("a")
        .endMetadata()
        .withData(data)
        .build();
  }
}
