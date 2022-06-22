package io.javaoperatorsdk.operator.api.reconciler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface EventSources {

  /**
   *
   * @return declarations of
   *         {@link io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource}
   */
  Informer[] informers() default {};

  /**
   * @return non informer based (external resource) event sources
   */
  EventSource[] others() default {};
}
