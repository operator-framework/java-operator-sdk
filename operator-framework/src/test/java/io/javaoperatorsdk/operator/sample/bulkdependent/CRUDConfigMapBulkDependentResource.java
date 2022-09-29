package io.javaoperatorsdk.operator.sample.bulkdependent;

import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;

public class CRUDConfigMapBulkDependentResource extends ConfigMapDeleterBulkDependentResource
    implements GarbageCollected<BulkDependentTestCustomResource> {
}
