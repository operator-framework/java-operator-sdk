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
package io.javaoperatorsdk.operator.api.config.dependent;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ConfiguredDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentConverter;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;

import static io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfigurationResolverTest.CustomAnnotationReconciler.DR_NAME;
import static org.junit.jupiter.api.Assertions.*;

class DependentResourceConfigurationResolverTest {

  // subclass to expose configFor method to this test class
  private static final class TestConfigurationService extends BaseConfigurationService {

    @Override
    protected <P extends HasMetadata>
        io.javaoperatorsdk.operator.api.config.ControllerConfiguration<P> configFor(
            Reconciler<P> reconciler) {
      return super.configFor(reconciler);
    }
  }

  private final TestConfigurationService configurationService = new TestConfigurationService();

  private <P extends HasMetadata>
      io.javaoperatorsdk.operator.api.config.ControllerConfiguration<P> configFor(
          Reconciler<P> reconciler) {
    // ensure that a new configuration is created each time
    return configurationService.configFor(reconciler);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Object extractDependentKubernetesResourceConfig(
      io.javaoperatorsdk.operator.api.config.ControllerConfiguration<?> configuration,
      Class<? extends DependentResource> target) {
    final var spec =
        configuration.getWorkflowSpec().orElseThrow().getDependentResourceSpecs().stream()
            .filter(s -> target.isAssignableFrom(s.getDependentResourceClass()))
            .findFirst()
            .orElseThrow();
    return configuration.getConfigurationFor(spec);
  }

  @Test
  void controllerConfigurationProvidedShouldBeReturnedIfAvailable() {
    final var cfg = configFor(new CustomAnnotationReconciler());

    final var customConfig =
        extractDependentKubernetesResourceConfig(cfg, CustomAnnotatedDep.class);
    assertInstanceOf(CustomConfig.class, customConfig);
    assertEquals(CustomAnnotatedDep.PROVIDED_VALUE, ((CustomConfig) customConfig).getValue());
    final var newConfig = new CustomConfig(72);
    final var overridden =
        ControllerConfigurationOverrider.override(cfg)
            .replacingNamedDependentResourceConfig(DR_NAME, newConfig)
            .build();
    final var spec =
        cfg.getWorkflowSpec().orElseThrow().getDependentResourceSpecs().stream()
            .filter(s -> DR_NAME.equals(s.getName()))
            .findFirst()
            .orElseThrow();
    assertEquals(newConfig, overridden.getConfigurationFor(spec));
  }

  @Test
  void getConverterShouldWork() {
    // extracting configuration should trigger converter creation
    configFor(new CustomAnnotationReconciler());
    var converter = DependentResourceConfigurationResolver.getConverter(CustomAnnotatedDep.class);
    assertNotNull(converter);
    assertEquals(CustomConfigConverter.class, converter.getClass());

    converter = DependentResourceConfigurationResolver.getConverter(ChildCustomAnnotatedDep.class);
    assertNotNull(converter);
    assertEquals(CustomConfigConverter.class, converter.getClass());
    assertEquals(
        DependentResourceConfigurationResolver.getConverter(CustomAnnotatedDep.class), converter);
  }

  @SuppressWarnings("rawtypes")
  @Test
  void registerConverterShouldWork() {
    final var overriddenConverter =
        new ConfigurationConverter() {

          @Override
          public Object configFrom(
              Annotation configAnnotation,
              DependentResourceSpec spec,
              io.javaoperatorsdk.operator.api.config.ControllerConfiguration parentConfiguration) {
            return null;
          }
        };
    DependentResourceConfigurationResolver.registerConverter(ServiceDep.class, overriddenConverter);
    configFor(new CustomAnnotationReconciler());

    // non overridden dependents should use the default converter
    var converter = DependentResourceConfigurationResolver.getConverter(ConfigMapDep.class);
    assertInstanceOf(KubernetesDependentConverter.class, converter);

    // dependent with registered converter should use that one
    converter = DependentResourceConfigurationResolver.getConverter(ServiceDep.class);
    assertEquals(overriddenConverter, converter);
  }

  @Workflow(
      dependents = {
        @Dependent(type = CustomAnnotatedDep.class, name = DR_NAME),
        @Dependent(type = ChildCustomAnnotatedDep.class),
        @Dependent(type = ConfigMapDep.class),
        @Dependent(type = ServiceDep.class)
      })
  @ControllerConfiguration
  static class CustomAnnotationReconciler implements Reconciler<ConfigMap> {

    public static final String DR_NAME = "first";

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context)
        throws Exception {
      return null;
    }
  }

  public static class ConfigMapDep extends KubernetesDependentResource<ConfigMap, ConfigMap>
      implements GarbageCollected<ConfigMap> {}

  public static class ServiceDep extends KubernetesDependentResource<Service, ConfigMap>
      implements GarbageCollected<ConfigMap> {}

  @CustomAnnotation(value = CustomAnnotatedDep.PROVIDED_VALUE)
  @Configured(
      by = CustomAnnotation.class,
      with = CustomConfig.class,
      converter = CustomConfigConverter.class)
  private static class CustomAnnotatedDep
      implements DependentResource<ConfigMap, ConfigMap>,
          ConfiguredDependentResource<CustomConfig>,
          GarbageCollected<ConfigMap> {

    public static final int PROVIDED_VALUE = 42;
    private CustomConfig config;

    @Override
    public ReconcileResult<ConfigMap> reconcile(ConfigMap primary, Context<ConfigMap> context) {
      return null;
    }

    @Override
    public Class<ConfigMap> resourceType() {
      return ConfigMap.class;
    }

    @Override
    public void configureWith(CustomConfig config) {
      this.config = config;
    }

    @Override
    public Optional<CustomConfig> configuration() {
      return Optional.ofNullable(config);
    }

    @Override
    public void delete(ConfigMap primary, Context<ConfigMap> context) {}
  }

  private static class ChildCustomAnnotatedDep extends CustomAnnotatedDep {}

  @Retention(RetentionPolicy.RUNTIME)
  private @interface CustomAnnotation {

    int value();
  }

  private static class CustomConfig {

    private final int value;

    private CustomConfig(int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }
  }

  private static class CustomConfigConverter
      implements ConfigurationConverter<CustomAnnotation, CustomConfig> {

    static final int CONVERTER_PROVIDED_DEFAULT = 7;

    @Override
    public CustomConfig configFrom(
        CustomAnnotation configAnnotation,
        DependentResourceSpec<?, ?, CustomConfig> spec,
        io.javaoperatorsdk.operator.api.config.ControllerConfiguration<?> parentConfiguration) {
      if (configAnnotation == null) {
        return new CustomConfig(CONVERTER_PROVIDED_DEFAULT);
      } else {
        return new CustomConfig(configAnnotation.value());
      }
    }
  }
}
