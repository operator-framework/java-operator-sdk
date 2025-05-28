package io.javaoperatorsdk.operator;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.config.ExecutorServiceManager;
import io.javaoperatorsdk.operator.processing.Controller;

/**
 * Not to be confused with the controller manager concept from Go's controller-runtime project. In
 * JOSDK, the equivalent concept is {@link Operator}.
 */
class ControllerManager {

  public static final String CANNOT_REGISTER_MULTIPLE_CONTROLLERS_WITH_SAME_NAME_MESSAGE =
      "Cannot register multiple controllers with same name: ";
  private static final Logger log = LoggerFactory.getLogger(ControllerManager.class);

  @SuppressWarnings("rawtypes")
  private final Map<String, Controller> controllers = new HashMap<>();

  private boolean started = false;
  private final ExecutorServiceManager executorServiceManager;

  public ControllerManager(ExecutorServiceManager executorServiceManager) {
    this.executorServiceManager = executorServiceManager;
  }

  public synchronized void shouldStart() {
    if (started) {
      return;
    }
    if (controllers.isEmpty()) {
      throw new OperatorException("No Controller exists. Exiting!");
    }
  }

  public synchronized void start(boolean startEventProcessor) {
    executorServiceManager.boundedExecuteAndWaitForAllToComplete(
        controllers().stream(),
        c -> {
          c.start(startEventProcessor);
          return null;
        },
        c -> "Controller Starter for: " + c.getConfiguration().getName());
    started = true;
  }

  public synchronized void stop() {
    executorServiceManager.boundedExecuteAndWaitForAllToComplete(
        controllers().stream(),
        c -> {
          log.debug("closing {}", c);
          c.stop();
          return null;
        },
        c -> "Controller Stopper for: " + c.getConfiguration().getName());
    started = false;
  }

  public synchronized void startEventProcessing() {
    executorServiceManager.boundedExecuteAndWaitForAllToComplete(
        controllers().stream(),
        c -> {
          c.startEventProcessing();
          return null;
        },
        c -> "Event processor starter for: " + c.getConfiguration().getName());
  }

  @SuppressWarnings("rawtypes")
  synchronized void add(Controller controller) {
    final var configuration = controller.getConfiguration();
    final var name = configuration.getName();
    if (controllers.containsKey(name)) {
      throw new OperatorException(
          CANNOT_REGISTER_MULTIPLE_CONTROLLERS_WITH_SAME_NAME_MESSAGE + name);
    }
    controllers.put(name, controller);
  }

  @SuppressWarnings("rawtypes")
  synchronized Optional<Controller> get(String name) {
    return Optional.ofNullable(controllers.get(name));
  }

  @SuppressWarnings("rawtypes")
  synchronized Collection<Controller> controllers() {
    return controllers.values();
  }

  synchronized int size() {
    return controllers.size();
  }
}
