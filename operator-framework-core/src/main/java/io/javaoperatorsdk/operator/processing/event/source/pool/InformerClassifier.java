package io.javaoperatorsdk.operator.processing.event.source.pool;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.informer.FieldSelector;

public record InformerClassifier(
    String labelSelector,
    String namespaceIdentifier,
    Class<? extends HasMetadata> resourceClass,
    FieldSelector fieldSelector) {}
