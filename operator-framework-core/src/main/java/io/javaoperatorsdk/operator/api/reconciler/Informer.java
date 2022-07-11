package io.javaoperatorsdk.operator.api.reconciler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryToSecondaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.filter.VoidGenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.VoidOnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.VoidOnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.VoidOnUpdateFilter;
import io.javaoperatorsdk.operator.processing.event.source.informer.OwnerReferenceSecondaryToPrimaryMapper;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.GENERATED_EVENT_SOURCE_NAME;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Informer {

  String name() default GENERATED_EVENT_SOURCE_NAME;

  Class<? extends HasMetadata> resourceType();

  String labelSelector() default Constants.NO_VALUE_SET;

  String[] namespaces() default Constants.DEFAULT_NAMESPACE;

  boolean followNamespaceChanges() default false;

  Class<? extends SecondaryToPrimaryMapper> secondaryToPrimaryMapper() default OwnerReferenceSecondaryToPrimaryMapper.class;

  Class<? extends PrimaryToSecondaryMapper> primaryToSecondaryMapper() default VoidPrimaryToSecondaryMapper.class;

  Class<? extends Predicate<? extends HasMetadata>> onAddFilter() default VoidOnAddFilter.class;

  Class<? extends BiPredicate<? extends HasMetadata, ? extends HasMetadata>> onUpdateFilter() default VoidOnUpdateFilter.class;

  Class<? extends BiPredicate<? extends HasMetadata, Boolean>> onDeleteFilter() default VoidOnDeleteFilter.class;

  Class<? extends Predicate<? extends HasMetadata>> genericFilter() default VoidGenericFilter.class;
}
