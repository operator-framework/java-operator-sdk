package io.javaoperatorsdk.operator.processing.event.source;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

import java.util.Set;

public abstract class AbstractSecondaryToPrimaryMapper<R> implements SecondaryToPrimaryMapper<R> {

    protected Class<? extends HasMetadata> primaryResourceType;

    @Override
    public void setPrimaryResourceType(Class<? extends HasMetadata> primaryResourceType) {
        this.primaryResourceType = primaryResourceType;
    }
    
}
