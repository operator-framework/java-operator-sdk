package io.javaoperatorsdk.operator.api.reconciler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.filter.VoidGenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.VoidOnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.VoidOnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.VoidOnUpdateFilter;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.GENERATED_EVENT_SOURCE_NAME;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface EventSource {

  String name() default GENERATED_EVENT_SOURCE_NAME;

  Class<? extends io.javaoperatorsdk.operator.processing.event.source.EventSource> type();

  Class<? extends Predicate<? extends HasMetadata>> onAddFilter() default VoidOnAddFilter.class;

  Class<? extends BiPredicate<? extends HasMetadata, ? extends HasMetadata>> onUpdateFilter() default VoidOnUpdateFilter.class;

  Class<? extends BiPredicate<? extends HasMetadata, Boolean>> onDeleteFilter() default VoidOnDeleteFilter.class;

  Class<? extends Predicate<? extends HasMetadata>> genericFilter() default VoidGenericFilter.class;
}
