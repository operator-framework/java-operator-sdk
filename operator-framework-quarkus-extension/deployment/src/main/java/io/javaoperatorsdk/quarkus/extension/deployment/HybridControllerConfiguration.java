package io.javaoperatorsdk.quarkus.extension.deployment;

import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.javaoperatorsdk.quarkus.extension.ExternalConfiguration;
import io.javaoperatorsdk.quarkus.extension.ExternalControllerConfiguration;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Type;
import org.jboss.jandex.Type.Kind;

/**
 * Encapsulates controller configuration values that might come from either annotation or external
 * properties file.
 */
class HybridControllerConfiguration {

  private final ValueExtractor extractor;
  private final String name;
  private final ExternalControllerConfiguration extConfig;

  /**
   * Creates a new HybridControllerConfiguration
   *
   * @param resourceControllerClassName the fully-qualified name of the associated {@link
   *     ResourceController} class
   * @param externalConfiguration the external configuration
   * @param controllerAnnotation the {@link io.javaoperatorsdk.operator.api.Controller} annotation
   *     associated with the controller
   */
  public HybridControllerConfiguration(
      String resourceControllerClassName,
      ExternalConfiguration externalConfiguration,
      AnnotationInstance controllerAnnotation) {
    // retrieve the controller's name
    final var defaultControllerName =
        ControllerUtils.getDefaultResourceControllerName(resourceControllerClassName);
    this.name =
        ValueExtractor.annotationValueOrDefault(
            controllerAnnotation, "name", AnnotationValue::asString, () -> defaultControllerName);

    this.extConfig = externalConfiguration.controllers.get(name);
    this.extractor = new ValueExtractor(controllerAnnotation, extConfig);
  }

  String name() {
    return name;
  }

  String finalizer(final String crdName) {
    return extractor.extract(
        c -> c.finalizer,
        "finalizerName",
        AnnotationValue::asString,
        () -> ControllerUtils.getDefaultFinalizerName(crdName));
  }

  boolean generationAware() {
    return extractor.extract(
        c -> c.generationAware,
        "generationAwareEventProcessing",
        AnnotationValue::asBoolean,
        () -> true);
  }

  String[] namespaces() {
    return extractor.extract(
        c -> c.namespaces.map(l -> l.toArray(new String[0])),
        "namespaces",
        AnnotationValue::asStringArray,
        () -> new String[] {});
  }

  RetryConfiguration retryConfiguration() {
    return extConfig == null ? null : RetryConfigurationResolver.resolve(extConfig.retry);
  }

  Type eventType() {
    return extractor.extract(
        c ->
            c.delayRegistrationUntilEvent
                .filter(s -> void.class.getName().equals(s))
                .map(DotName::createSimple)
                .map(dn -> Type.create(dn, Kind.CLASS)),
        "delayRegistrationUntilEvent",
        AnnotationValue::asClass,
        () -> null);
  }

  boolean delayedRegistration() {
    return extractor.extract(
        c -> c.delayRegistrationUntilEvent.map(s -> void.class.getName().equals(s)),
        "delayRegistrationUntilEvent",
        v -> v.asClass().kind() != Kind.VOID,
        () -> false);
  }
}
