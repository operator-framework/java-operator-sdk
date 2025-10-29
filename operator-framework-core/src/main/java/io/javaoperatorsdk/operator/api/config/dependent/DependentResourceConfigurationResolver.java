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
import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings({"rawtypes", "unchecked"})
public class DependentResourceConfigurationResolver {

  private DependentResourceConfigurationResolver() {}

  private static final Map<Class<? extends DependentResource>, ConverterAnnotationPair> converters =
      new HashMap<>();
  private static final Map<Class<? extends ConfigurationConverter>, ConfigurationConverter>
      knownConverters = new HashMap<>();

  public static <C extends ControllerConfiguration<?>> void configureSpecFromConfigured(
      DependentResourceSpec spec,
      C parentConfiguration,
      Class<? extends DependentResource> dependentResourceClass) {
    var converterAnnotationPair = converters.get(dependentResourceClass);

    Annotation configAnnotation;
    if (converterAnnotationPair == null) {
      var configuredClassPair = getConfigured(dependentResourceClass);
      if (configuredClassPair == null) {
        return;
      }

      // check if we already have a converter registered for the found Configured annotated class
      converterAnnotationPair = converters.get(configuredClassPair.annotatedClass);
      if (converterAnnotationPair == null) {
        final var configured = configuredClassPair.configured;
        converterAnnotationPair =
            getOrCreateConverter(
                dependentResourceClass,
                parentConfiguration,
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
    final var config = converter.configFrom(configAnnotation, spec, parentConfiguration);
    spec.setNullableConfiguration(config);
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

  private static <C extends ControllerConfiguration<? extends HasMetadata>>
      ConverterAnnotationPair getOrCreateConverter(
          Class<? extends DependentResource> dependentResourceClass,
          C parentConfiguration,
          Class<? extends ConfigurationConverter> converterClass,
          Class<? extends Annotation> annotationClass) {
    var converterPair = converters.get(dependentResourceClass);
    if (converterPair == null) {
      // only instantiate a new converter if we haven't done so already for this converter type
      var converter = knownConverters.get(converterClass);
      if (converter == null) {
        converter =
            Utils.instantiate(
                converterClass,
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
  public static void registerConverter(
      Class<? extends DependentResource> dependentResourceClass, ConfigurationConverter converter) {
    var configured = getConfigured(dependentResourceClass);
    if (configured == null) {
      throw new IllegalArgumentException(
          "There is no @"
              + Configured.class.getSimpleName()
              + " annotation on "
              + dependentResourceClass.getName()
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

  /** To support independent unit tests */
  public static void clear() {
    converters.clear();
    knownConverters.clear();
  }

  private static class ConfiguredClassPair {
    private final Configured configured;
    private final Class<? extends DependentResource> annotatedClass;

    private ConfiguredClassPair(
        Configured configured, Class<? extends DependentResource> annotatedClass) {
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

    private ConverterAnnotationPair(
        ConfigurationConverter converter, Class<? extends Annotation> annotationClass) {
      this.converter = converter;
      this.annotationClass = annotationClass;
    }

    @Override
    public String toString() {
      return converter.toString() + " -> " + annotationClass.getName();
    }
  }
}
