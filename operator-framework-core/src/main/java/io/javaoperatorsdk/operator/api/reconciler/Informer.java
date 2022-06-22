package io.javaoperatorsdk.operator.api.reconciler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.OwnerReferenceSecondaryToPrimaryMapper;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Informer {

  Class<? extends HasMetadata> resourceType();

  String labelSelector() default Constants.NO_VALUE_SET;

  String[] namespaces() default Constants.DEFAULT_NAMESPACE;

  boolean followNamespaceChanges() default false;

  Class<? extends SecondaryToPrimaryMapper> secondaryToPrimaryMapper() default OwnerReferenceSecondaryToPrimaryMapper.class;

  // todo add filters
}
