package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.ResourceController;
import java.util.Set;

/** An interface from which to retrieve configuration information. */
public interface ConfigurationService {

  /**
   * Retrieves the configuration associated with the specified controller
   *
   * @param controller the controller we want the configuration of
   * @param <R> the {@code CustomResource} type associated with the specified controller
   * @return the {@link ControllerConfiguration} associated with the specified controller or {@code
   *     null} if no configuration exists for the controller
   */
  <R extends CustomResource> ControllerConfiguration<R> getConfigurationFor(
      ResourceController<R> controller);

  /**
   * Retrieves the Kubernetes client configuration
   *
   * @return the configuration of the Kubernetes client, defaulting to the provided
   *     auto-configuration
   */
  default Config getClientConfiguration() {
    return Config.autoConfigure(null);
  }

  /**
   * Retrieves the set of the names of controllers for which a configuration exists
   *
   * @return the set of known controller names
   */
  Set<String> getKnownControllerNames();

  /**
   * Retrieves the {@link Version} information associated with this particular instance of the SDK
   *
   * @return the version information
   */
  Version getVersion();

  /**
   * Whether the operator should query the CRD to make sure it's deployed and validate {@link
   * CustomResource} implementations before attempting to register the associated controllers.
   *
   * <p>Note that this might require elevating the privileges associated with the operator to gain
   * read access on the CRD resources.
   *
   * @return {@code true} if CRDs should be checked (default), {@code false} otherwise
   */
  default boolean validateCustomResources() {
    return true;
  }
}
