package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;

public class AnnotationSecondaryToPrimaryMapper<R extends HasMetadata>
    implements SecondaryToPrimaryMapper<R> {
  @Override
  public Set<ResourceID> toPrimaryResourceIDs(R dependentResource) {
    return Mappers.fromDefaultAnnotations().toPrimaryResourceIDs(dependentResource);
  }
}
