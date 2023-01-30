package io.javaoperatorsdk.operator.sample.primarytosecondarydependent;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryToSecondaryMapper;

import java.util.Set;

public class SecondaryToPrimaryMapperDependentResource extends
        KubernetesDependentResource<ConfigMap,PrimaryToSecondaryDependentCustomResource>
        implements PrimaryToSecondaryMapper<PrimaryToSecondaryDependentCustomResource> {

    public SecondaryToPrimaryMapperDependentResource() {
        super(ConfigMap.class);
    }


    @Override
    public Set<ResourceID> toSecondaryResourceIDs(PrimaryToSecondaryDependentCustomResource primary) {
        return null;
    }
}
