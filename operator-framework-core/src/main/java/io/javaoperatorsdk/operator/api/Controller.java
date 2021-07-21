package io.javaoperatorsdk.operator.api;

import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEventFilter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Controller {

  String NULL = "";
  String WATCH_CURRENT_NAMESPACE = "JOSDK_WATCH_CURRENT";
  String NO_FINALIZER = "JOSDK_NO_FINALIZER";

  String name() default NULL;

  /**
   * Optional finalizer name, if it is not provided, one will be automatically generated. If the
   * provided value is the value specified by {@link #NO_FINALIZER}, then no finalizer will be added
   * to custom resources.
   *
   * @return the finalizer name
   */
  String finalizerName() default NULL;

  /**
   * If true, will dispatch new event to the controller if generation increased since the last
   * processing, otherwise will process all events. See generation meta attribute <a href=
   * "https://kubernetes.io/docs/tasks/access-kubernetes-api/custom-resources/custom-resource-definitions/#status-subresource">here</a>
   *
   * @return whether the controller takes generation into account to process events
   */
  boolean generationAwareEventProcessing() default true;

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
  String labelSelector() default NULL;


  /**
   * Optional list of classes providing custom {@link CustomResourceEventFilter}.
   *
   * @return the list of event filters.
   */
  @SuppressWarnings("rawtypes")
  Class<CustomResourceEventFilter>[] eventFilters() default {};
}
