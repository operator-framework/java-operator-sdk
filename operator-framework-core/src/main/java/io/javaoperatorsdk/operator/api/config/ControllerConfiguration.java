package io.javaoperatorsdk.operator.api.config;

import java.lang.reflect.ParameterizedType;
import java.util.Collections;
import java.util.Set;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Controller;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEventFilters;

public interface ControllerConfiguration<R extends CustomResource<?, ?>> {

  default String getName() {
    return ControllerUtils.getDefaultResourceReconcilerName(getAssociatedControllerClassName());
  }

  default String getCRDName() {
    return CustomResource.getCRDName(getCustomResourceClass());
  }

  default String getFinalizer() {
    return ControllerUtils.getDefaultFinalizerName(getCRDName());
  }

  /**
   * Retrieves the label selector that is used to filter which custom resources are actually watched
   * by the associated controller. See
   * https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/ for more details on
   * syntax.
   *
   * @return the label selector filtering watched custom resources
   */
  default String getLabelSelector() {
    return null;
  }

  default boolean isGenerationAware() {
    return true;
  }

  default Class<R> getCustomResourceClass() {
    ParameterizedType type = (ParameterizedType) getClass().getGenericInterfaces()[0];
    return (Class<R>) type.getActualTypeArguments()[0];
  }

  String getAssociatedControllerClassName();

  default Set<String> getNamespaces() {
    return Collections.emptySet();
  }

  default boolean watchAllNamespaces() {
    return allNamespacesWatched(getNamespaces());
  }

  static boolean allNamespacesWatched(Set<String> namespaces) {
    return namespaces == null || namespaces.isEmpty();
  }

  default boolean watchCurrentNamespace() {
    return currentNamespaceWatched(getNamespaces());
  }

  static boolean currentNamespaceWatched(Set<String> namespaces) {
    return namespaces != null
        && namespaces.size() == 1
        && namespaces.contains(Controller.WATCH_CURRENT_NAMESPACE);
  }

  /**
   * Computes the effective namespaces based on the set specified by the user, in particular
   * retrieves the current namespace from the client when the user specified that they wanted to
   * watch the current namespace only.
   *
   * @return a Set of namespace names the associated controller will watch
   */
  default Set<String> getEffectiveNamespaces() {
    var targetNamespaces = getNamespaces();
    if (watchCurrentNamespace()) {
      final var parent = getConfigurationService();
      if (parent == null) {
        throw new IllegalStateException(
            "Parent ConfigurationService must be set before calling this method");
      }
      targetNamespaces = Collections.singleton(parent.getClientConfiguration().getNamespace());
    }
    return targetNamespaces;
  }

  default RetryConfiguration getRetryConfiguration() {
    return RetryConfiguration.DEFAULT;
  }

  ConfigurationService getConfigurationService();

  default void setConfigurationService(ConfigurationService service) {}

  default boolean useFinalizer() {
    return !Controller.NO_FINALIZER.equals(getFinalizer());
  }

  /**
   * Allow controllers to filter events before they are provided to the
   * {@link io.javaoperatorsdk.operator.processing.event.EventHandler}. Note that the provided
   * filter is combined with {@link #isGenerationAware()} to compute the final set of fiolters that
   * should be applied;
   *
   * @return filter
   */
  default CustomResourceEventFilter<R> getEventFilter() {
    return CustomResourceEventFilters.passthrough();
  }
}
