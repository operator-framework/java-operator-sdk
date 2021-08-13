package io.javaoperatorsdk.operator;

import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Version;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.event.DefaultEventSourceManager;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
public class Operator implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(Operator.class);
  private final KubernetesClient k8sClient;
  private final ConfigurationService configurationService;
  private final List<Closeable> closeables;
  private final Object lock;
  private final List<ControllerRef> controllers;
  private volatile boolean started;

  public Operator(KubernetesClient k8sClient, ConfigurationService configurationService) {
    this.k8sClient = k8sClient;
    this.configurationService = configurationService;
    this.closeables = new ArrayList<>();
    this.lock = new Object();
    this.controllers = new ArrayList<>();
    this.started = false;

    Runtime.getRuntime().addShutdownHook(new Thread(this::close));
  }

  public KubernetesClient getKubernetesClient() {
    return k8sClient;
  }

  public ConfigurationService getConfigurationService() {
    return configurationService;
  }

  /**
   * Finishes the operator startup process. This is mostly used in injection-aware applications
   * where there is no obvious entrypoint to the application which can trigger the injection process
   * and start the cluster monitoring processes.
   */
  @SuppressWarnings("unchecked")
  public void start() {
    synchronized (lock) {
      if (started) {
        return;
      }

      final var version = configurationService.getVersion();
      log.info(
          "Operator SDK {} (commit: {}) built on {} starting...",
          version.getSdkVersion(),
          version.getCommit(),
          version.getBuiltTime());
      log.info("Client version: {}", Version.clientVersion());
      try {
        final var k8sVersion = k8sClient.getVersion();
        if (k8sVersion != null) {
          log.info("Server version: {}.{}", k8sVersion.getMajor(), k8sVersion.getMinor());
        }
      } catch (Exception e) {
        log.error("Error retrieving the server version. Exiting!", e);
        throw new OperatorException("Error retrieving the server version", e);
      }

      for (ControllerRef ref : controllers) {
        startController(ref.controller, ref.configuration);
      }

      started = true;
    }
  }

  /** Stop the operator. */
  @Override
  public void close() {
    synchronized (lock) {
      if (!started) {
        return;
      }

      log.info(
          "Operator SDK {} is shutting down...", configurationService.getVersion().getSdkVersion());

      for (Closeable closeable : this.closeables) {
        try {
          log.debug("closing {}", closeable);
          closeable.close();
        } catch (IOException e) {
          log.warn("Error closing {}", closeable, e);
        }
      }

      started = false;
    }
  }

  /**
   * Add a registration requests for the specified controller with this operator. The effective
   * registration of the controller is delayed till the operator is started.
   *
   * @param controller the controller to register
   * @param <R> the {@code CustomResource} type associated with the controller
   * @throws OperatorException if a problem occurred during the registration process
   */
  public <R extends CustomResource> void register(ResourceController<R> controller)
      throws OperatorException {
    register(controller, null);
  }

  /**
   * Add a registration requests for the specified controller with this operator, overriding its
   * default configuration by the specified one (usually created via {@link
   * io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider#override(ControllerConfiguration)},
   * passing it the controller's original configuration. The effective registration of the
   * controller is delayed till the operator is started.
   *
   * @param controller the controller to register
   * @param configuration the configuration with which we want to register the controller, if {@code
   *     null}, the controller's original configuration is used
   * @param <R> the {@code CustomResource} type associated with the controller
   * @throws OperatorException if a problem occurred during the registration process
   */
  public <R extends CustomResource> void register(
      ResourceController<R> controller, ControllerConfiguration<R> configuration)
      throws OperatorException {
    synchronized (lock) {
      if (!started) {
        this.controllers.add(new ControllerRef(controller, configuration));
      } else {
        this.controllers.add(new ControllerRef(controller, configuration));
        startController(controller, configuration);
      }
    }
  }

  /**
   * Registers the specified controller with this operator, overriding its default configuration by
   * the specified one (usually created via {@link
   * io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider#override(ControllerConfiguration)},
   * passing it the controller's original configuration.
   *
   * @param controller the controller to register
   * @param configuration the configuration with which we want to register the controller, if {@code
   *     null}, the controller's original configuration is used
   * @param <R> the {@code CustomResource} type associated with the controller
   * @throws OperatorException if a problem occurred during the registration process
   */
  private <R extends CustomResource> void startController(
      ResourceController<R> controller, ControllerConfiguration<R> configuration)
      throws OperatorException {

    final var existing = configurationService.getConfigurationFor(controller);
    if (existing == null) {
      log.warn(
          "Skipping registration of {} controller named {} because its configuration cannot be found.\n"
              + "Known controllers are: {}",
          controller.getClass().getCanonicalName(),
          ControllerUtils.getNameFor(controller),
          configurationService.getKnownControllerNames());
    } else {
      if (configuration == null) {
        configuration = existing;
      }

      final Class<R> resClass = configuration.getCustomResourceClass();
      final String controllerName = configuration.getName();
      final var crdName = configuration.getCRDName();
      final var specVersion = "v1";

      // check that the custom resource is known by the cluster if configured that way
      final CustomResourceDefinition crd; // todo: check proper CRD spec version based on config
      if (configurationService.checkCRDAndValidateLocalModel()) {
        crd = k8sClient.apiextensions().v1().customResourceDefinitions().withName(crdName).get();
        if (crd == null) {
          throwMissingCRDException(crdName, specVersion, controllerName);
        }

        // Apply validations that are not handled by fabric8
        CustomResourceUtils.assertCustomResource(resClass, crd);
      }

      try {
        DefaultEventSourceManager eventSourceManager =
            new DefaultEventSourceManager(
                controller, configuration, k8sClient.customResources(resClass));
        controller.init(eventSourceManager);
        closeables.add(eventSourceManager);
      } catch (MissingCRDException e) {
        throwMissingCRDException(crdName, specVersion, controllerName);
      }

      if (failOnMissingCurrentNS(configuration)) {
        throw new OperatorException(
            "Controller '"
                + controllerName
                + "' is configured to watch the current namespace but it couldn't be inferred from the current configuration.");
      }

      final var watchedNS =
          configuration.watchAllNamespaces()
              ? "[all namespaces]"
              : configuration.getEffectiveNamespaces();
      log.info(
          "Registered Controller: '{}' for CRD: '{}' for namespace(s): {}",
          controllerName,
          resClass,
          watchedNS);
    }
  }

  private void throwMissingCRDException(String crdName, String specVersion, String controllerName) {
    throw new MissingCRDException(
        crdName,
        specVersion,
        "'"
            + crdName
            + "' "
            + specVersion
            + " CRD was not found on the cluster, controller '"
            + controllerName
            + "' cannot be registered");
  }

  /**
   * Determines whether we should fail because the current namespace is request as target namespace
   * but is missing
   *
   * @return {@code true} if the current namespace is requested but is missing, {@code false}
   *     otherwise
   */
  private static <R extends CustomResource> boolean failOnMissingCurrentNS(
      ControllerConfiguration<R> configuration) {
    if (configuration.watchCurrentNamespace()) {
      final var effectiveNamespaces = configuration.getEffectiveNamespaces();
      return effectiveNamespaces.size() == 1
          && effectiveNamespaces.stream().allMatch(Objects::isNull);
    }
    return false;
  }

  private static class ControllerRef {
    public final ResourceController controller;
    public final ControllerConfiguration configuration;

    public ControllerRef(ResourceController controller, ControllerConfiguration configuration) {
      this.controller = controller;
      this.configuration = configuration;
    }
  }
}
