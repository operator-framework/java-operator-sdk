package io.javaoperatorsdk.operator.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.EMPTY_STRING;

@Target({ElementType.TYPE})
public @interface KubernetesDependent {

  boolean OWNED_DEFAULT = true;
  boolean SKIP_UPDATE_DEFAULT = true;

  boolean owned() default OWNED_DEFAULT;

  boolean skipUpdateIfUnchanged() default SKIP_UPDATE_DEFAULT;

  /**
   * Specified which namespaces this Controller monitors for custom resources events. If no
   * namespace is specified then the controller will monitor all namespaces by default.
   *
   * @return the list of namespaces this controller monitors
   */
  String[] namespaces() default {};

  /**
   * Optional label selector used to identify the set of custom resources the controller will acc
   * upon. The label selector can be made of multiple comma separated requirements that acts as a
   * logical AND operator.
   *
   * @return the label selector
   */
  String labelSelector() default EMPTY_STRING;
}
