package com.github.containersolutions.operator.processing.event.internal;

import com.github.containersolutions.operator.processing.event.AbstractEventSource;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 *  This is a simplistic impl of the delayed event source.
 *  Possible improvements:
 *   - if an event is scheduled already for a CR cancel that and schedule the new one
 */
public class DelayedEventSource extends AbstractEventSource {
    public static final String DEFAULT_NAME = "DelayedReprocessEventSource";

    // todo shared thread pool?
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);

    public void scheduleDelayedEvent(String resourceUid, long milliseconds) {
        executor.schedule(new EventTrigger(resourceUid), milliseconds, TimeUnit.MILLISECONDS);
    }

    private class EventTrigger implements Runnable {
        private String resourceId;

        public EventTrigger(String resourceId) {
            this.resourceId = resourceId;
        }

        @Override
        public void run() {
            eventHandler.handleEvent(new DelayedReprocessEvent(resourceId, DelayedEventSource.this));
        }
    }

}
