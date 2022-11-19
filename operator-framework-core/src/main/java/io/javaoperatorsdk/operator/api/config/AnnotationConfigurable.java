package io.javaoperatorsdk.operator.api.config;

import java.lang.annotation.Annotation;

public interface AnnotationConfigurable<A extends Annotation> {
  void initFrom(A configuration);
}
