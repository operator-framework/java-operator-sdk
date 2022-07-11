package io.javaoperatorsdk.operator.api.config;

import java.lang.annotation.Annotation;

public interface AnnotationConfigurable<C extends Annotation> {
  void initFrom(C configuration);
}
