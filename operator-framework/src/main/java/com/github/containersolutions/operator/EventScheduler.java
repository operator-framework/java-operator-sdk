package com.github.containersolutions.operator;


import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Requirements:
 * <ul>
 *   <li>Only 1 event should be processed at a time for same custom resource
 *   (metadata.name is the id, but kind and api should be taken into account)</li>
 *   <li>Done - If event processing fails it should be rescheduled with retry - with limited number of retried
 *   and exponential time slacks (pluggable reschedule strategy in future?)</li>
 *   <li>if there are multiple events received for the same resource process only the last one. (Others can be discarded)
 *   User resourceVersion to check which is the latest. Put the new one at the and of the queue?
 *   </li>
 *   <li>Done - Avoid starvation, so on retry put back resource at the end of the queue.</li>
 *   <li>The selecting event from a queue should not be naive. So for example:
 *     If we cannot pick the last event because an event for that resource is currently processing just gor for the next one.
 *     (Maybe is good to represent this queue with a list.) Or if and event is rescheduled
 *     (skip if there is not enough time left since last execution)
 *   </li>
 *   <li>Impossible, scheduled chosen - Threading approach thus thread pool size and/or implementation should be configurable</li>
 *   <li>see also: https://github.com/ContainerSolutions/java-operator-sdk/issues/34</li>
 * </ul>
 *
 * @param <R>
 */


public class EventScheduler<R extends CustomResource> implements Watcher<R> {

    private final static ExponentialBackOff backOff = new ExponentialBackOff();

    private final static Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    private AtomicBoolean processingEnabled = new AtomicBoolean(false);

    private final EventDispatcher eventDispatcher;

    private final ScheduledThreadPoolExecutor executor;

    EventScheduler(EventDispatcher<R> eventDispatcher) {
        this.eventDispatcher = eventDispatcher;

        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("event-scheduler-%d")
                .setDaemon(true)
                .build();
        executor = new ScheduledThreadPoolExecutor(1, threadFactory);
    }

    void startProcessing() {
        processingEnabled.set(true);
    }

    @Override
    public void eventReceived(Watcher.Action action, R resource) {
        if (!processingEnabled.get()) return;

        log.info("Event received for action: {}, {}: {}", action, resource.getClass().getSimpleName(),
                resource.getMetadata().getName());

        executor.execute(new EventConsumer(new CustomResourceEvent(action, resource), this.eventDispatcher, backOff.start(), this.executor));
    }

    @Override
    public void onClose(KubernetesClientException e) {
        processingEnabled.set(false);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            log.error("It was not possible to finish all threads, Killed them.");
        }
    }

}


