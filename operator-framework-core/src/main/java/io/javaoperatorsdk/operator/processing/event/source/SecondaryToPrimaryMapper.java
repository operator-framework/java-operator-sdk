package io.javaoperatorsdk.operator.processing.event.source;

import io.javaoperatorsdk.operator.processing.event.ResourceID;
import java.util.Set;

@FunctionalInterface
public interface SecondaryToPrimaryMapper<R> {
  Set<ResourceID> toPrimaryResourceIDs(R resource);
}
