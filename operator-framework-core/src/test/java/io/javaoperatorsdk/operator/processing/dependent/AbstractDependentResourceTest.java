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

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractDependentResourceTest {

  @Test
  void throwsExceptionIfDesiredIsNullOnCreate() {
    TestDependentResource testDependentResource = new TestDependentResource();
    testDependentResource.setSecondary(null);
    testDependentResource.setDesired(null);

    assertThrows(
        DependentResourceException.class,
        () -> testDependentResource.reconcile(new TestCustomResource(), null));
  }

  @Test
  void throwsExceptionIfDesiredIsNullOnUpdate() {
    TestDependentResource testDependentResource = new TestDependentResource();
    testDependentResource.setSecondary(configMap());
    testDependentResource.setDesired(null);

    assertThrows(
        DependentResourceException.class,
        () -> testDependentResource.reconcile(new TestCustomResource(), null));
  }

  @Test
  void throwsExceptionIfCreateReturnsNull() {
    TestDependentResource testDependentResource = new TestDependentResource();
    testDependentResource.setSecondary(null);
    testDependentResource.setDesired(configMap());

    assertThrows(
        DependentResourceException.class,
        () -> testDependentResource.reconcile(new TestCustomResource(), null));
  }

  @Test
  void throwsExceptionIfUpdateReturnsNull() {
    TestDependentResource testDependentResource = new TestDependentResource();
    testDependentResource.setSecondary(configMap());
    testDependentResource.setDesired(configMap());

    assertThrows(
        DependentResourceException.class,
        () -> testDependentResource.reconcile(new TestCustomResource(), null));
  }

  private ConfigMap configMap() {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(
        new ObjectMetaBuilder().withName("test").withNamespace("default").build());
    return configMap;
  }

  private static class TestDependentResource
      extends AbstractDependentResource<ConfigMap, TestCustomResource>
      implements Creator<ConfigMap, TestCustomResource>, Updater<ConfigMap, TestCustomResource> {

    private ConfigMap secondary;
    private ConfigMap desired;

    @Override
    public Class<ConfigMap> resourceType() {
      return ConfigMap.class;
    }

    @Override
    public Optional<ConfigMap> getSecondaryResource(
        TestCustomResource primary, Context<TestCustomResource> context) {
      return Optional.ofNullable(secondary);
    }

    @Override
    protected Optional<ConfigMap> selectTargetSecondaryResource(
        Set<ConfigMap> secondaryResources,
        TestCustomResource primary,
        Context<TestCustomResource> context) {
      if (secondaryResources.size() == 1) {
        return Optional.of(secondaryResources.iterator().next());
      } else if (secondaryResources.isEmpty()) {
        return Optional.empty();
      } else {
        throw new IllegalStateException();
      }
    }

    @Override
    protected void onCreated(
        TestCustomResource primary, ConfigMap created, Context<TestCustomResource> context) {}

    @Override
    protected void onUpdated(
        TestCustomResource primary,
        ConfigMap updated,
        ConfigMap actual,
        Context<TestCustomResource> context) {}

    @Override
    protected ConfigMap desired(TestCustomResource primary, Context<TestCustomResource> context) {
      return desired;
    }

    public ConfigMap getSecondary() {
      return secondary;
    }

    public TestDependentResource setSecondary(ConfigMap secondary) {
      this.secondary = secondary;
      return this;
    }

    public ConfigMap getDesired() {
      return desired;
    }

    public TestDependentResource setDesired(ConfigMap desired) {
      this.desired = desired;
      return this;
    }

    @Override
    public ConfigMap create(
        ConfigMap desired, TestCustomResource primary, Context<TestCustomResource> context) {
      return null;
    }

    @Override
    public ConfigMap update(
        ConfigMap actual,
        ConfigMap desired,
        TestCustomResource primary,
        Context<TestCustomResource> context) {
      return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Matcher.Result<ConfigMap> match(
        ConfigMap actualResource, TestCustomResource primary, Context<TestCustomResource> context) {
      var result = mock(Matcher.Result.class);
      when(result.matched()).thenReturn(false);
      return result;
    }
  }
}
