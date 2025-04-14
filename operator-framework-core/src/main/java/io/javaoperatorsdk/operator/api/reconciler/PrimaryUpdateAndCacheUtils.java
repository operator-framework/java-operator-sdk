package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class PrimaryUpdateAndCacheUtils {

  public static <P extends HasMetadata> P updateAndCacheStatus(P primary, Context<P> context) {
    if (primary.getMetadata().getResourceVersion() == null) {
      throw new IllegalStateException(
          "Primary resource version is null, it is expected to set resource version for updates"
              + " with for cache");
    }
    var updatedResource = context.getClient().resource(primary).updateStatus();
    context
        .eventSourceRetriever()
        .getControllerEventSource()
        .handleRecentResourceUpdate(ResourceID.fromResource(primary), updatedResource, primary);
    return updatedResource;
  }

  public static <P extends HasMetadata> P patchAndCacheStatus(P primary) {
    return null;
  }
}
