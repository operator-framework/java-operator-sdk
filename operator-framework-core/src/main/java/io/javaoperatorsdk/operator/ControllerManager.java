package io.javaoperatorsdk.operator;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.processing.Controller;

/**
 * Not to be confused with the controller manager concept from Go's controller-runtime project. In
 * JOSDK, the equivalent concept is {@link Operator}.
 */
class ControllerManager {

  private static final Logger log = LoggerFactory.getLogger(ControllerManager.class);

  @SuppressWarnings("rawtypes")
  private final Map<String, Controller> controllers = new HashMap<>();
  private boolean started = false;

  public synchronized void shouldStart() {
    if (started) {
      return;
    }
    if (controllers.isEmpty()) {
      throw new OperatorException("No Controller exists. Exiting!");
    }
  }

  public synchronized void start(boolean startEventProcessor) {
    controllers().parallelStream().forEach(c -> c.start(startEventProcessor));
    started = true;
  }

  public synchronized void stop() {
    controllers().parallelStream().forEach(closeable -> {
      log.debug("closing {}", closeable);
      closeable.stop();
    });
    started = false;
  }

  public synchronized void startEventProcessing() {
    controllers().parallelStream().forEach(Controller::startEventProcessing);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  synchronized void add(Controller controller) {
    final var configuration = controller.getConfiguration();
    final var resourceTypeName = ReconcilerUtils
        .getResourceTypeNameWithVersion(configuration.getResourceClass());
    final var existing = controllers.get(resourceTypeName);
    if (existing != null) {
      throw new OperatorException("Cannot register controller '" + configuration.getName()
          + "': another controller named '" + existing.getConfiguration().getName()
          + "' is already registered for resource '" + resourceTypeName + "'");
    }
    controllers.put(resourceTypeName, controller);
  }

  @SuppressWarnings("rawtypes")
  synchronized Optional<Controller> get(String name) {
    return controllers().stream()
        .filter(c -> name.equals(c.getConfiguration().getName()))
        .findFirst();
  }

  @SuppressWarnings("rawtypes")
  synchronized Collection<Controller> controllers() {
    return controllers.values();
  }

  synchronized int size() {
    return controllers.size();
  }
}

