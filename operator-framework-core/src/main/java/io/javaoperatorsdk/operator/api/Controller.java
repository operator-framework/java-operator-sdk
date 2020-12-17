package io.javaoperatorsdk.operator.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Controller {

  String NULL = "";

  String crdName();

  String name() default NULL;

  /**
   * Optional finalizer name, if it is not, the crdName will be used as the name of the finalizer
   * too.
   */
  String finalizerName() default NULL;

  /**
   * If true, will dispatch new event to the controller if generation increased since the last
   * processing, otherwise will process all events. See generation meta attribute <a
   * href="https://kubernetes.io/docs/tasks/access-kubernetes-api/custom-resources/custom-resource-definitions/#status-subresource">here</a>
   */
  boolean generationAwareEventProcessing() default true;

  boolean isClusterScoped() default false;

  String[] namespaces() default {};
}
