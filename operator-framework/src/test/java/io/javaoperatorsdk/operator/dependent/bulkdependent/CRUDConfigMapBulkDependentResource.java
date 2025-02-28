package io.javaoperatorsdk.operator.dependent.bulkdependent;

import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;

public class CRUDConfigMapBulkDependentResource extends ConfigMapDeleterBulkDependentResource
    implements GarbageCollected<BulkDependentTestCustomResource> {}
