package io.javaoperatorsdk.operator.api.reconciler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.GENERATED_EVENT_SOURCE_NAME;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface EventSource {

  String name() default GENERATED_EVENT_SOURCE_NAME;

  Class<? extends io.javaoperatorsdk.operator.processing.event.source.EventSource> type();

  // todo add filters
}
