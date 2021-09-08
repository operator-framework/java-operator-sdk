package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.client.CustomResource;
import java.util.Set;

/**
 * @deprecated use {@link DefaultControllerConfiguration} instead
 * @param <R>
 */
@Deprecated
public class AbstractControllerConfiguration<R extends CustomResource<?, ?>>
    extends DefaultControllerConfiguration<R> {

  @Deprecated
  public AbstractControllerConfiguration(String associatedControllerClassName, String name,
      String crdName, String finalizer, boolean generationAware,
      Set<String> namespaces,
      RetryConfiguration retryConfiguration) {
    super(associatedControllerClassName, name, crdName, finalizer, generationAware, namespaces,
        retryConfiguration);
  }

  public AbstractControllerConfiguration(String associatedControllerClassName, String name,
      String crdName, String finalizer, boolean generationAware,
      Set<String> namespaces,
      RetryConfiguration retryConfiguration, String labelSelector,
      Class<R> customResourceClass,
      ConfigurationService service) {
    super(associatedControllerClassName, name, crdName, finalizer, generationAware, namespaces,
        retryConfiguration, labelSelector, customResourceClass, service);
  }
}
