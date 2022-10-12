package io.javaoperatorsdk.operator.sample.bulkdependent;

import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;

public class CRUDConfigMapDynamicallyCreatedDependentResource extends
    ConfigMapDeleterDynamicallyCreatedDependentResource
    implements GarbageCollected<DynamicDependentTestCustomResource> {
}
