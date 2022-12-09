package io.javaoperatorsdk.operator.api.config.dependent;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.DependentResourceConfigurator;

@SuppressWarnings({"rawtypes", "unchecked"})
public class DependentResourceConfigurationResolver {

  private DependentResourceConfigurationResolver() {}

  private static final Map<Class<? extends DependentResource>, ConverterAnnotationPair> converters =
      new HashMap<>();
  private static final Map<Class<? extends ConfigurationConverter>, ConfigurationConverter> knownConverters =
      new HashMap<>();

  public static <C extends ControllerConfiguration<? extends HasMetadata>> void configure(
      DependentResource dependentResource, DependentResourceSpec spec, C parentConfiguration) {
    if (dependentResource instanceof DependentResourceConfigurator) {
      final var configurator = (DependentResourceConfigurator) dependentResource;
      final var config = configurationFor(spec, parentConfiguration);
      configurator.configureWith(config);
    }
  }

  public static <C extends ControllerConfiguration<? extends HasMetadata>> Object configurationFor(
      DependentResourceSpec spec, C parentConfiguration) {

    // first check if the parent configuration has potentially already resolved the configuration
    if (parentConfiguration instanceof DependentResourceConfigurationProvider) {
      final var provider = (DependentResourceConfigurationProvider) parentConfiguration;
      final var configuration = provider.getConfigurationFor(spec);
      if (configuration != null) {
        return configuration;
      }
    }

    // find Configured-annotated class if it exists
    return extractConfigurationFromConfigured(spec.getDependentResourceClass(),
        parentConfiguration);
  }

  public static <C extends ControllerConfiguration<? extends HasMetadata>> Object extractConfigurationFromConfigured(
      Class<? extends DependentResource> dependentResourceClass, C parentConfiguration) {
    var converterAnnotationPair = converters.get(dependentResourceClass);

    Annotation configAnnotation;
    if (converterAnnotationPair == null) {
      var configuredClassPair = getConfigured(dependentResourceClass);
      if (configuredClassPair == null) {
        return null;
      }

      // check if we already have a converter registered for the found Configured annotated class
      converterAnnotationPair = converters.get(configuredClassPair.annotatedClass);
      if (converterAnnotationPair == null) {
        final var configured = configuredClassPair.configured;
        converterAnnotationPair =
            getOrCreateConverter(dependentResourceClass, parentConfiguration,
                configured.converter(),
                configured.by());
      } else {
        // only register the converter pair for this dependent resource class as well
        converters.put(dependentResourceClass, converterAnnotationPair);
      }
    }

    // find the associated configuration annotation
    configAnnotation =
        dependentResourceClass.getAnnotation(converterAnnotationPair.annotationClass);
    final var converter = converterAnnotationPair.converter;

    // always called even if the annotation is null so that implementations can provide default
    // values
    return converter.configFrom(configAnnotation, parentConfiguration, dependentResourceClass);
  }

  private static ConfiguredClassPair getConfigured(
      Class<? extends DependentResource> dependentResourceClass) {
    Class<? extends DependentResource> currentClass = dependentResourceClass;
    Configured configured;
    ConfiguredClassPair result = null;
    while (DependentResource.class.isAssignableFrom(currentClass)) {
      configured = currentClass.getAnnotation(Configured.class);
      if (configured != null) {
        result = new ConfiguredClassPair(configured, currentClass);
        break;
      }
      currentClass = (Class<? extends DependentResource>) currentClass.getSuperclass();
    }
    return result;
  }

  private static <C extends ControllerConfiguration<? extends HasMetadata>> ConverterAnnotationPair getOrCreateConverter(
      Class<? extends DependentResource> dependentResourceClass, C parentConfiguration,
      Class<? extends ConfigurationConverter> converterClass,
      Class<? extends Annotation> annotationClass) {
    var converterPair = converters.get(dependentResourceClass);
    if (converterPair == null) {
      // only instantiate a new converter if we haven't done so already for this converter type
      var converter = knownConverters.get(converterClass);
      if (converter == null) {
        converter = Utils.instantiate(converterClass,
            ConfigurationConverter.class,
            Utils.contextFor(parentConfiguration, dependentResourceClass, Configured.class));
        knownConverters.put(converterClass, converter);
      }
      // record dependent class - converter association for faster future retrieval
      converterPair = new ConverterAnnotationPair(converter, annotationClass);
      converters.put(dependentResourceClass, converterPair);
    }
    return converterPair;
  }

  static ConfigurationConverter getConverter(
      Class<? extends DependentResource> dependentResourceClass) {
    final var converterAnnotationPair = converters.get(dependentResourceClass);
    return converterAnnotationPair != null ? converterAnnotationPair.converter : null;
  }

  @SuppressWarnings("unused")
  public static void registerConverter(Class<? extends DependentResource> dependentResourceClass,
      ConfigurationConverter converter) {
    var configured = getConfigured(dependentResourceClass);
    if (configured == null) {
      throw new IllegalArgumentException("There is no @" + Configured.class.getSimpleName()
          + " annotation on " + dependentResourceClass.getName()
          + " or its superclasses and thus doesn't need to be associated with a converter");
    }

    // find the associated configuration annotation
    final var toRegister = new ConverterAnnotationPair(converter, configured.configured.by());
    final Class<? extends ConfigurationConverter> converterClass = converter.getClass();
    converters.put(dependentResourceClass, toRegister);

    // also register the Configured-annotated class if not the one we're registering
    if (!dependentResourceClass.equals(configured.annotatedClass)) {
      converters.put(configured.annotatedClass, toRegister);
    }

    knownConverters.put(converterClass, converter);
  }

  private static class ConfiguredClassPair {
    private final Configured configured;
    private final Class<? extends DependentResource> annotatedClass;

    private ConfiguredClassPair(Configured configured,
        Class<? extends DependentResource> annotatedClass) {
      this.configured = configured;
      this.annotatedClass = annotatedClass;
    }

    @Override
    public String toString() {
      return annotatedClass.getName() + " -> " + configured;
    }
  }

  private static class ConverterAnnotationPair {
    private final ConfigurationConverter converter;
    private final Class<? extends Annotation> annotationClass;

    private ConverterAnnotationPair(ConfigurationConverter converter,
        Class<? extends Annotation> annotationClass) {
      this.converter = converter;
      this.annotationClass = annotationClass;
    }

    @Override
    public String toString() {
      return converter.toString() + " -> " + annotationClass.getName();
    }
  }
}
