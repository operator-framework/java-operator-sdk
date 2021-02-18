package io.javaoperatorsdk.quarkus.extension.deployment;

import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.quarkus.extension.ExternalControllerConfiguration;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;

class ValueExtractor {

  private final AnnotationInstance controllerAnnotation;
  private final ExternalControllerConfiguration extContConfig;

  ValueExtractor(
      AnnotationInstance controllerAnnotation, ExternalControllerConfiguration extContConfig) {
    this.controllerAnnotation = controllerAnnotation;
    this.extContConfig = extContConfig;
  }

  /**
   * Extracts the appropriate configuration value for the controller checking first any annotation
   * configuration, then potentially overriding it by a properties-provided value or returning a
   * default value if neither is provided.
   *
   * @param extractor       a Function extracting the optional value we're interested in from the
   *                        external configuration
   * @param annotationField the name of the {@link Controller} annotation we're want to retrieve if
   *                        present
   * @param converter       a Function converting the annotation value to the type we're expecting
   * @param defaultValue    a Supplier that computes/retrieve a default value when needed
   * @param <T>             the expected type of the configuration value we're trying to extract
   * @return the extracted configuration value
   */
  <T> T extract(
      Function<ExternalControllerConfiguration, Optional<T>> extractor,
      String annotationField,
      Function<AnnotationValue, T> converter,
      Supplier<T> defaultValue) {
    // first check if we have an external configuration
    if (extContConfig != null) {
      // extract value from config if present
      return extractor
          .apply(extContConfig)
          // or get from the annotation or default
          .orElse(annotationValueOrDefault(annotationField, converter, defaultValue));
    } else {
      // get from annotation or default
      return annotationValueOrDefault(annotationField, converter, defaultValue);
    }
  }

  private <T> T annotationValueOrDefault(
      String name, Function<AnnotationValue, T> converter, Supplier<T> defaultValue) {
    return annotationValueOrDefault(controllerAnnotation, name, converter, defaultValue);
  }

  static <T> T annotationValueOrDefault(
      AnnotationInstance annotation,
      String name,
      Function<AnnotationValue, T> converter,
      Supplier<T> defaultValue) {
    return annotation != null
        ?
        // get converted annotation value of get default
        Optional.ofNullable(annotation.value(name)).map(converter).orElseGet(defaultValue)
        :
            // get default
            defaultValue.get();
  }
}
