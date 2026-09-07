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
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DefaultContext;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DesiredStateAspect;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractDependentResourceTest {

  private static final TestCustomResource PRIMARY = new TestCustomResource();
  private static final DefaultContext<TestCustomResource> CONTEXT = createContext(PRIMARY);

  private static DefaultContext<TestCustomResource> createContext(TestCustomResource primary) {
    return createContext(primary, List.of());
  }

  private static DefaultContext<TestCustomResource> createContext(
      TestCustomResource primary, List<DesiredStateAspect> aspects) {
    final ConfigurationService configurationService = mock();
    when(configurationService.desiredStateAspects()).thenReturn(aspects);
    final ControllerConfiguration<TestCustomResource> controllerConfiguration = mock();
    when(controllerConfiguration.getConfigurationService()).thenReturn(configurationService);
    final Controller<TestCustomResource> controller = mock();
    when(controller.getConfiguration()).thenReturn(controllerConfiguration);
    return new DefaultContext<>(mock(), controller, primary, false, false);
  }

  @Test
  void throwsExceptionIfDesiredIsNullOnCreate() {
    TestDependentResource testDependentResource = new TestDependentResource();
    testDependentResource.setSecondary(null);
    testDependentResource.setDesired(null);

    assertThrows(
        DependentResourceException.class, () -> testDependentResource.reconcile(PRIMARY, CONTEXT));
  }

  @Test
  void throwsExceptionIfDesiredIsNullOnUpdate() {
    TestDependentResource testDependentResource = new TestDependentResource();
    testDependentResource.setSecondary(configMap());
    testDependentResource.setDesired(null);

    assertThrows(
        DependentResourceException.class, () -> testDependentResource.reconcile(PRIMARY, CONTEXT));
  }

  @Test
  void throwsExceptionIfCreateReturnsNull() {
    TestDependentResource testDependentResource = new TestDependentResource();
    testDependentResource.setSecondary(null);
    testDependentResource.setDesired(configMap());

    assertThrows(
        DependentResourceException.class, () -> testDependentResource.reconcile(PRIMARY, CONTEXT));
  }

  @Test
  void throwsExceptionIfUpdateReturnsNull() {
    TestDependentResource testDependentResource = new TestDependentResource();
    testDependentResource.setSecondary(configMap());
    testDependentResource.setDesired(configMap());

    assertThrows(
        DependentResourceException.class, () -> testDependentResource.reconcile(PRIMARY, CONTEXT));
  }

  @Test
  void checkThatDesiredIsOnlyCalledOnce() {
    final var testDependentResource = new DesiredCallCountCheckingDR();
    final var primary = new TestCustomResource();
    final var spec = primary.getSpec();
    spec.setConfigMapName("foo");
    spec.setKey("key");
    spec.setValue("value");
    final var context = createContext(primary);
    testDependentResource.reconcile(primary, context);

    spec.setValue("value2");
    testDependentResource.reconcile(primary, context);

    assertEquals(1, testDependentResource.desiredCallCount);

    context.getOrComputeDesiredStateFor(
        testDependentResource, p -> testDependentResource.desired(p, context));
    assertEquals(1, testDependentResource.desiredCallCount);
  }

  @Test
  void appliesConfiguredDesiredStateAspectsInOrderAndOnlyOnce() {
    final var testDependentResource = new DesiredCallCountCheckingDR();
    final var primary = new TestCustomResource();
    final var spec = primary.getSpec();
    spec.setConfigMapName("foo");
    spec.setKey("key");
    spec.setValue("value");
    final var context =
        createContext(
            primary,
            List.of(
                (desired, dependentResource, ctx) -> {
                  assertSame(testDependentResource, dependentResource);
                  assertSame(primary, ctx.getPrimaryResource());
                  desired.getMetadata().getLabels().put("aspect", "first");
                },
                (desired, dependentResource, ctx) ->
                    desired.getMetadata().getLabels().put("aspect", "second")));

    final var created = testDependentResource.reconcile(primary, context).getSingleResource();
    assertEquals("second", created.orElseThrow().getMetadata().getLabels().get("aspect"));

    // desired state is cached, aspects should therefore not be applied again
    created.orElseThrow().getMetadata().getLabels().remove("aspect");
    testDependentResource.reconcile(primary, context);
    assertNull(created.orElseThrow().getMetadata().getLabels().get("aspect"));
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

    public void setSecondary(ConfigMap secondary) {
      this.secondary = secondary;
    }

    public void setDesired(ConfigMap desired) {
      this.desired = desired;
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

  private static class DesiredCallCountCheckingDR extends TestDependentResource {
    private short desiredCallCount;

    @Override
    public ConfigMap update(
        ConfigMap actual,
        ConfigMap desired,
        TestCustomResource primary,
        Context<TestCustomResource> context) {
      return desired;
    }

    @Override
    public ConfigMap create(
        ConfigMap desired, TestCustomResource primary, Context<TestCustomResource> context) {
      return desired;
    }

    @Override
    protected ConfigMap desired(TestCustomResource primary, Context<TestCustomResource> context) {
      final var spec = primary.getSpec();
      desiredCallCount++;
      return new ConfigMapBuilder()
          .editOrNewMetadata()
          .withName(spec.getConfigMapName())
          .endMetadata()
          .addToData(spec.getKey(), spec.getValue())
          .build();
    }
  }
}
