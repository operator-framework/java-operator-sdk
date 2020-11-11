package com.github.containersolutions.operator.processing;

import com.github.containersolutions.operator.processing.event.Event;
import com.github.containersolutions.operator.processing.event.internal.CustomResourceEvent;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher;

import java.util.List;

public class ProcessingUtils {

    public static String getUID(CustomResource customResource) {
        return customResource.getMetadata().getUid();
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
