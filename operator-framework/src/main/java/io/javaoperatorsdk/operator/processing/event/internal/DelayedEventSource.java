package io.javaoperatorsdk.operator.processing.event.internal;

import io.javaoperatorsdk.operator.processing.event.AbstractEventSource;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DelayedEventSource extends AbstractEventSource {

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
