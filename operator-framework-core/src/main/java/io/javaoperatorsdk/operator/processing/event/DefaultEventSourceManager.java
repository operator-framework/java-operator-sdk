package io.javaoperatorsdk.operator.processing.event;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.MissingCRDException;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.ConfiguredController;
import io.javaoperatorsdk.operator.processing.DefaultEventHandler;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.internal.TimerEventSource;

public class DefaultEventSourceManager<R extends CustomResource<?, ?>>
    implements EventSourceManager {

  private static final Logger log = LoggerFactory.getLogger(DefaultEventSourceManager.class);

  private final ReentrantLock lock = new ReentrantLock();
  private final List<EventSource> eventSources = Collections.synchronizedList(new ArrayList<>());
  private DefaultEventHandler<R> defaultEventHandler;
  private TimerEventSource<R> retryTimerEventSource;
  private CustomResourceEventSource customResourceEventSource;

  DefaultEventSourceManager(DefaultEventHandler<R> defaultEventHandler) {
    init(defaultEventHandler);
  }

  public DefaultEventSourceManager(ConfiguredController<R> controller) {
    customResourceEventSource = new CustomResourceEventSource<>(controller);
    init(new DefaultEventHandler<>(controller, customResourceEventSource));
    registerEventSource(customResourceEventSource);
  }

  private void init(DefaultEventHandler<R> defaultEventHandler) {
    this.defaultEventHandler = defaultEventHandler;
    defaultEventHandler.setEventSourceManager(this);

    this.retryTimerEventSource = new TimerEventSource<>();
    registerEventSource(retryTimerEventSource);
  }

  @Override
  public void close() {
    lock.lock();
    try {
      try {
        defaultEventHandler.close();
      } catch (Exception e) {
        log.warn("Error closing event handler", e);
      }
      log.debug("Closing event sources.");
      for (var eventSource : eventSources) {
        try {
          eventSource.close();
        } catch (Exception e) {
          log.warn("Error closing {} -> {}", eventSource);
        }
      }
      eventSources.clear();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public final void registerEventSource(EventSource eventSource)
      throws OperatorException {
    Objects.requireNonNull(eventSource, "EventSource must not be null");
    lock.lock();
    try {
      eventSources.add(eventSource);
      eventSource.setEventHandler(defaultEventHandler);
      eventSource.start();
    } catch (Throwable e) {
      if (e instanceof IllegalStateException || e instanceof MissingCRDException) {
        // leave untouched
        throw e;
      }
      throw new OperatorException(
          "Couldn't register event source: " + eventSource.getClass().getName(), e);
    } finally {
      lock.unlock();
    }
  }

  public void cleanupForCustomResource(CustomResourceID customResourceUid) {
    lock.lock();
    try {
      for (EventSource eventSource : this.eventSources) {
        eventSource.cleanupForCustomResource(customResourceUid);
      }
    } finally {
      lock.unlock();
    }
  }

  public TimerEventSource getRetryTimerEventSource() {
    return retryTimerEventSource;
  }

  @Override
  public List<EventSource> getRegisteredEventSources() {
    return Collections.unmodifiableList(eventSources);
  }

  @Override
  public CustomResourceEventSource getCustomResourceEventSource() {
    return customResourceEventSource;
  }

}
