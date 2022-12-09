package io.javaoperatorsdk.operator.api.config.dependent;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Configured {

  Class<? extends Annotation> by();

  Class<?> with();

  @SuppressWarnings("rawtypes")
  Class<? extends ConfigurationConverter> converter();
}
