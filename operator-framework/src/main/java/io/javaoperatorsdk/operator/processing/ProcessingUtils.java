package io.javaoperatorsdk.operator.processing;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.Watcher;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEvent;

import java.util.List;

public class ProcessingUtils {

    public static String getUID(HasMetadata customResource) {
        return customResource.getMetadata().getUid();
    }

    public static String getVersion(HasMetadata customResource) {
        return customResource.getMetadata().getResourceVersion();
    }

    public static boolean containsCustomResourceDeletedEvent(List<Event> events) {
        return events.stream().anyMatch(e -> {
            if (e instanceof CustomResourceEvent) {
                return ((CustomResourceEvent) e).getAction() == Watcher.Action.DELETED;
            } else {
                return false;
            }
        });
    }
}
