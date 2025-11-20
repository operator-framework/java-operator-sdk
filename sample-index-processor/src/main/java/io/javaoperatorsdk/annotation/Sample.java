package io.javaoperatorsdk.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark integration tests as samples for documentation generation. Tests annotated
 * with @Sample will be included in the generated samples.md documentation file.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Sample {

  /**
   * A short title describing the test sample.
   *
   * @return the short title
   */
  String tldr();

  /**
   * A detailed description of what the test does and demonstrates.
   *
   * @return the detailed description
   */
  String description();
}
