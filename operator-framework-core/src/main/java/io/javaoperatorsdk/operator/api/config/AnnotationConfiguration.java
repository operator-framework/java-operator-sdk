package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import java.util.Optional;
import java.util.function.Predicate;

public class AnnotationConfiguration<R extends CustomResource> extends AbstractConfiguration<R> {
  private final Optional<Controller> annotation;

  public AnnotationConfiguration(
      Class<? extends ResourceController<R>> controllerClass, Class<R> customResourceClass) {
    super(controllerClass, customResourceClass);
    this.annotation = Optional.ofNullable(controllerClass.getAnnotation(Controller.class));
  }

  @Override
  public String getFinalizer() {
    return annotation
        .map(Controller::finalizerName)
        .filter(Predicate.not(String::isBlank))
        .orElse(ControllerUtils.getDefaultFinalizerName(getCRDName()));
  }

  @Override
  public boolean isGenerationAware() {
    return annotation.map(Controller::generationAwareEventProcessing).orElse(true);
  }

  @Override
  public String getLabelSelector() {
    return annotation.map(Controller::labelSelector).orElse("");
  }
}
