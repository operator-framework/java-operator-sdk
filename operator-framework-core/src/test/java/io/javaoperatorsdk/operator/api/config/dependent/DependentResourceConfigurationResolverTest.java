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
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.DependentResourceConfigurator;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentConverter;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;

import static io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfigurationResolverTest.CustomAnnotationReconciler.DR_NAME;
import static org.junit.jupiter.api.Assertions.*;

class DependentResourceConfigurationResolverTest {

  // subclass to expose configFor method to this test class
  private final static class TestConfigurationService extends BaseConfigurationService {

    @Override
    protected <P extends HasMetadata> io.javaoperatorsdk.operator.api.config.ControllerConfiguration<P> configFor(
        Reconciler<P> reconciler) {
      return super.configFor(reconciler);
    }
  }

  private final TestConfigurationService configurationService = new TestConfigurationService();

  private <P extends HasMetadata> io.javaoperatorsdk.operator.api.config.ControllerConfiguration<P> configFor(
      Reconciler<P> reconciler) {
    // ensure that a new configuration is created each time
    return configurationService.configFor(reconciler);
  }

  @Test
  void controllerConfigurationProvidedShouldBeReturnedIfAvailable() {
    final var cfg = configFor(new CustomAnnotationReconciler());
    final var customConfig = DependentResourceConfigurationResolver
        .extractConfigurationFromConfigured(CustomAnnotatedDep.class, cfg);
    assertTrue(customConfig instanceof CustomConfig);
    assertEquals(CustomAnnotatedDep.PROVIDED_VALUE, ((CustomConfig) customConfig).getValue());
    final var newConfig = new CustomConfig(72);
    final var overridden = ControllerConfigurationOverrider.override(cfg)
        .replacingNamedDependentResourceConfig(DR_NAME, newConfig)
        .build();
    final var spec = cfg.getWorkflowSpec().orElseThrow().getDependentResourceSpecs().stream()
        .filter(s -> DR_NAME.equals(s.getName()))
        .findFirst()
        .orElseThrow();
    assertEquals(newConfig,
        DependentResourceConfigurationResolver.configurationFor(spec, overridden));
  }

  @Test
  void getConverterShouldWork() {
    final var cfg = configFor(new CustomAnnotationReconciler());
    var converter = DependentResourceConfigurationResolver.getConverter(CustomAnnotatedDep.class);
    assertNull(converter);
    assertNull(DependentResourceConfigurationResolver.getConverter(ChildCustomAnnotatedDep.class));

    // extracting configuration should trigger converter creation
    DependentResourceConfigurationResolver.extractConfigurationFromConfigured(
        CustomAnnotatedDep.class, cfg);
    converter = DependentResourceConfigurationResolver.getConverter(CustomAnnotatedDep.class);
    assertNotNull(converter);
    assertEquals(CustomConfigConverter.class, converter.getClass());

    converter = DependentResourceConfigurationResolver.getConverter(ChildCustomAnnotatedDep.class);
    assertNull(converter);
    DependentResourceConfigurationResolver.extractConfigurationFromConfigured(
        ChildCustomAnnotatedDep.class, cfg);
    converter = DependentResourceConfigurationResolver.getConverter(ChildCustomAnnotatedDep.class);
    assertNotNull(converter);
    assertEquals(CustomConfigConverter.class, converter.getClass());
    assertEquals(DependentResourceConfigurationResolver.getConverter(CustomAnnotatedDep.class),
        converter);
  }

  @SuppressWarnings("rawtypes")
  @Test
  void registerConverterShouldWork() {
    final var cfg = configFor(new CustomAnnotationReconciler());
    var converter = DependentResourceConfigurationResolver.getConverter(ConfigMapDep.class);
    assertNull(converter);
    DependentResourceConfigurationResolver.extractConfigurationFromConfigured(ConfigMapDep.class,
        cfg);
    converter = DependentResourceConfigurationResolver.getConverter(ConfigMapDep.class);
    assertTrue(converter instanceof KubernetesDependentConverter);
    final var overriddenConverter = new ConfigurationConverter() {
      @Override
      public Object configFrom(Annotation configAnnotation,
          io.javaoperatorsdk.operator.api.config.ControllerConfiguration parentConfiguration,
          Class originatingClass) {
        return null;
      }
    };
    DependentResourceConfigurationResolver.registerConverter(KubernetesDependentResource.class,
        overriddenConverter);

    // already resolved converters are kept unchanged
    converter = DependentResourceConfigurationResolver.getConverter(ConfigMapDep.class);
    assertTrue(converter instanceof KubernetesDependentConverter);

    // but new converters should use the overridden version
    DependentResourceConfigurationResolver.extractConfigurationFromConfigured(ServiceDep.class,
        cfg);
    converter = DependentResourceConfigurationResolver.getConverter(ServiceDep.class);
    assertEquals(overriddenConverter, converter);
  }

  @Workflow(dependents = {
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

  private static class ConfigMapDep extends KubernetesDependentResource<ConfigMap, ConfigMap> {

    public ConfigMapDep() {
      super(ConfigMap.class);
    }
  }

  private static class ServiceDep extends KubernetesDependentResource<Service, ConfigMap> {

    public ServiceDep() {
      super(Service.class);
    }
  }

  @CustomAnnotation(value = CustomAnnotatedDep.PROVIDED_VALUE)
  @Configured(by = CustomAnnotation.class, with = CustomConfig.class,
      converter = CustomConfigConverter.class)
  private static class CustomAnnotatedDep implements DependentResource<ConfigMap, ConfigMap>,
      DependentResourceConfigurator<CustomConfig> {

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
  }

  private static class ChildCustomAnnotatedDep extends CustomAnnotatedDep {

  }

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
      implements ConfigurationConverter<CustomAnnotation, CustomConfig, CustomAnnotatedDep> {

    static final int CONVERTER_PROVIDED_DEFAULT = 7;

    @Override
    public CustomConfig configFrom(CustomAnnotation configAnnotation,
        io.javaoperatorsdk.operator.api.config.ControllerConfiguration<?> parentConfiguration,
        Class<CustomAnnotatedDep> originatingClass) {
      if (configAnnotation == null) {
        return new CustomConfig(CONVERTER_PROVIDED_DEFAULT);
      } else {
        return new CustomConfig(configAnnotation.value());
      }
    }
  }
}
