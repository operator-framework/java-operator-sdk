package com.github.containersolutions.operator;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class EventScheduler {

    Map<String, EventDispatcher> eventDispatchers;
    Map<String, CustomResource> customResourceMap = Collections.synchronizedMap(new HashMap<>());

    public <R extends CustomResource> void rescheduleEvent(Watcher.Action action, CustomResource resource) {
        // schedule the event in 3 seconds, then exponentionally increase (up to a configured value)
        // what if a new event arrives next in between?
    }

    public void eventArrived(CustomResource resource) {

    }


}
