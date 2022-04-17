package io.javaoperatorsdk.operator.processing.event.source.informer;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;

import java.util.Map;
import java.util.Set;

public class PrimaryToSecondaryIndexer<R extends HasMetadata,P extends HasMetadata > {

    private SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper;
    private Map<ResourceID, Set<ResourceID>> index;

    public PrimaryToSecondaryIndexer(SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper) {
        this.secondaryToPrimaryMapper = secondaryToPrimaryMapper;
    }


}
