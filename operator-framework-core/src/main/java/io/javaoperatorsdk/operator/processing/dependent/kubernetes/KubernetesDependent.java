package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_VALUE_SET;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface KubernetesDependent {

  String[] DEFAULT_NAMESPACES = {Constants.SAME_AS_CONTROLLER};

  /**
   * Specified which namespaces this Controller monitors for custom resources events. If no
   * namespace is specified then the controller will monitor the namespaces configured for the
   * controller.
   *
   * @return the list of namespaces this controller monitors
   */
  String[] namespaces() default {Constants.SAME_AS_CONTROLLER};

  /**
   * Optional label selector used to identify the set of custom resources the controller will acc
   * upon. The label selector can be made of multiple comma separated requirements that acts as a
   * logical AND operator.
   *
   * @return the label selector
   */
  String labelSelector() default NO_VALUE_SET;

  /**
   * Optional {@link OnAddFilter} to filter events sent to this KubernetesDependent
   *
   * @return the {@link OnAddFilter} filter implementation to use, defaulting to the interface
   *         itself if no value is set
   */
  Class<? extends OnAddFilter> onAddFilter() default OnAddFilter.class;

  /**
   * Optional {@link OnUpdateFilter} to filter events sent to this KubernetesDependent
   *
   * @return the {@link OnUpdateFilter} filter implementation to use, defaulting to the interface
   *         itself if no value is set
   */
  Class<? extends OnUpdateFilter> onUpdateFilter() default OnUpdateFilter.class;

  /**
   * Optional {@link OnDeleteFilter} to filter events sent to this KubernetesDependent
   *
   * @return the {@link OnDeleteFilter} filter implementation to use, defaulting to the interface
   *         itself if no value is set
   */
  Class<? extends OnDeleteFilter> onDeleteFilter() default OnDeleteFilter.class;

  /**
   * Optional {@link GenericFilter} to filter events sent to this KubernetesDependent
   *
   * @return the {@link GenericFilter} filter implementation to use, defaulting to the interface
   *         itself if no value is set
   */
  Class<? extends GenericFilter> genericFilter() default GenericFilter.class;
}
