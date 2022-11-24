package io.javaoperatorsdk.operator.api.config;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;
import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE_SET;

public interface ResourceConfiguration<R extends HasMetadata> {

  default String getResourceTypeName() {
    return ReconcilerUtils.getResourceTypeName(getResourceClass());
  }

  default Optional<OnAddFilter<R>> onAddFilter() {
    return Optional.empty();
  }

  default Optional<OnUpdateFilter<R>> onUpdateFilter() {
    return Optional.empty();
  }

  default Optional<GenericFilter<R>> genericFilter() {
    return Optional.empty();
  }

  /**
   * Retrieves the label selector that is used to filter which resources are actually watched by the
   * associated event source. See the official documentation on the
   * <a href="https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/">topic</a>
   * for more details on syntax.
   *
   * @return the label selector filtering watched resources
   */
  default String getLabelSelector() {
    return null;
  }

  @SuppressWarnings("unchecked")
  default Class<R> getResourceClass() {
    return (Class<R>) Utils.getFirstTypeArgumentFromSuperClassOrInterface(getClass(),
        ResourceConfiguration.class);
  }

  default Set<String> getNamespaces() {
    return DEFAULT_NAMESPACES_SET;
  }

  default boolean watchAllNamespaces() {
    return allNamespacesWatched(getNamespaces());
  }

  static boolean allNamespacesWatched(Set<String> namespaces) {
    failIfNotValid(namespaces);
    return DEFAULT_NAMESPACES_SET.equals(namespaces);
  }

  default boolean watchCurrentNamespace() {
    return currentNamespaceWatched(getNamespaces());
  }

  static boolean currentNamespaceWatched(Set<String> namespaces) {
    failIfNotValid(namespaces);
    return WATCH_CURRENT_NAMESPACE_SET.equals(namespaces);
  }

  static void failIfNotValid(Set<String> namespaces) {
    if (namespaces != null && !namespaces.isEmpty()) {
      final var present = namespaces.contains(Constants.WATCH_CURRENT_NAMESPACE)
          || namespaces.contains(Constants.WATCH_ALL_NAMESPACES);
      if (!present || namespaces.size() == 1) {
        return;
      }
    }
    throw new IllegalArgumentException(
        "Must specify namespaces. To watch all namespaces, use only '"
            + Constants.WATCH_ALL_NAMESPACES
            + "'. To watch only the namespace in which the operator is deployed, use only '"
            + Constants.WATCH_CURRENT_NAMESPACE + "'");
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
      final String namespace =
          ConfigurationServiceProvider.instance().getClientConfiguration().getNamespace();
      if (namespace == null) {
        throw new OperatorException(
            "Couldn't retrieve the currently connected namespace. Make sure it's correctly set in your ~/.kube/config file, using, e.g. 'kubectl config set-context <your context> --namespace=<your namespace>'");
      }
      targetNamespaces = Collections.singleton(namespace);
    }
    return targetNamespaces;
  }

  default Optional<ItemStore<R>> itemStore() {
    return Optional.empty();
  }
}
