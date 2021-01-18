package io.javaoperatorsdk.operator.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Controller {

  String NULL = "";

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

  /**
   * Specified which namespaces this Controller monitors for custom resources events. If no
   * namespace is specified then the controller will monitor the namespace it is deployed in (or the
   * namespace to which the Kubernetes client is connected to). To specify that the controller needs
   * to monitor all namespaces, add {@link
   * io.javaoperatorsdk.operator.api.config.ControllerConfiguration#WATCH_ALL_NAMESPACES_MARKER} to
   * this field.
   *
   * @return the list of namespaces this controller monitors
   */
  String[] namespaces() default {};
}
