package io.javaoperatorsdk.operator.processing.event.internal;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.processing.ProcessingUtils;
import io.javaoperatorsdk.operator.processing.event.AbstractEventSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class TimerEventSource extends AbstractEventSource {

    private Logger log = LoggerFactory.getLogger(TimerEventSource.class);

    private final Timer timer = new Timer();
    private final ReentrantLock lock = new ReentrantLock();

    private final Map<String, List<EventProducerTimeTask>> timerTasks = new ConcurrentHashMap<>();

    public void schedule(CustomResource customResource, long delay, long period) {
        String resourceUid = ProcessingUtils.getUID(customResource);
        EventProducerTimeTask task = new EventProducerTimeTask(resourceUid);
        storeTask(resourceUid, task);
        timer.schedule(task, delay, period);
    }

    public void scheduleOnce(CustomResource customResource, long delay) {
        String resourceUid = ProcessingUtils.getUID(customResource);
        OneTimeEventProducerTimerTask task = new OneTimeEventProducerTimerTask(resourceUid);
        storeTask(resourceUid, task);
        timer.schedule(task, delay);
    }

    private void storeTask(String resourceUid, EventProducerTimeTask task) {
        try {
            lock.lock();
            List<EventProducerTimeTask> tasks = getOrInitResourceRelatedTimers(resourceUid);
            tasks.add(task);
        } finally {
            lock.unlock();
        }
    }

    private List<EventProducerTimeTask> getOrInitResourceRelatedTimers(String resourceUid) {
        List<EventProducerTimeTask> actualList = timerTasks.get(resourceUid);
        if (actualList == null) {
            actualList = new ArrayList<>();
            timerTasks.put(resourceUid, actualList);
        }
        return actualList;
    }

    @Override
    public void eventSourceDeRegisteredForResource(String customResourceUid) {
        List<EventProducerTimeTask> tasks = getEventProducerTimeTask(customResourceUid);
        tasks.forEach(TimerTask::cancel);
        timerTasks.remove(customResourceUid);
    }

    /**
     * This just to cover possible corner cases user might have
     *
     * @param customResourceUid
     * @return
     */
    private List<EventProducerTimeTask> getEventProducerTimeTask(String customResourceUid) {
        List<EventProducerTimeTask> tasks = timerTasks.get(customResourceUid);
        if (tasks == null) {
            return Collections.EMPTY_LIST;
        }
        return tasks;
    }

    public class EventProducerTimeTask extends TimerTask {
        protected final String customResourceUid;

        public EventProducerTimeTask(String customResourceUid) {
            this.customResourceUid = customResourceUid;
        }

        @Override
        public void run() {
            log.debug("Producing event for custom resource id: {}", customResourceUid);
            eventHandler.handleEvent(new TimerEvent(customResourceUid, TimerEventSource.this));
        }
    }

    public class OneTimeEventProducerTimerTask extends EventProducerTimeTask {
        public OneTimeEventProducerTimerTask(String customResourceUid) {
            super(customResourceUid);
        }

        @Override
        public void run() {
            super.run();
            try {
                lock.lock();
                List<EventProducerTimeTask> tasks = timerTasks.get(customResourceUid);
                tasks.remove(this);
                if (tasks.isEmpty()) {
                    timerTasks.remove(customResourceUid);
                }
            } finally {
                lock.unlock();
            }
        }
    }

}
