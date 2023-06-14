package io.javaoperatorsdk.operator.api.config;

import java.util.Collection;
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

  default Optional<OnAddFilter<? super R>> onAddFilter() {
    return Optional.empty();
  }

  default Optional<OnUpdateFilter<? super R>> onUpdateFilter() {
    return Optional.empty();
  }

  default Optional<GenericFilter<? super R>> genericFilter() {
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

  static String ensureValidLabelSelector(String labelSelector) {
    // might want to implement validation here?
    return labelSelector;
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

  static Set<String> ensureValidNamespaces(Collection<String> namespaces) {
    if (namespaces != null && !namespaces.isEmpty()) {
      return Set.copyOf(namespaces);
    } else {
      return Constants.DEFAULT_NAMESPACES_SET;
    }
  }

  /**
   * Computes the effective namespaces based on the set specified by the user, in particular
   * retrieves the current namespace from the client when the user specified that they wanted to
   * watch the current namespace only.
   *
   * @return a Set of namespace names the associated controller will watch
   */
  default Set<String> getEffectiveNamespaces(ConfigurationService configurationService) {
    var targetNamespaces = getNamespaces();
    if (watchCurrentNamespace()) {
      final String namespace =
          configurationService.getClientConfiguration().getNamespace();
      if (namespace == null) {
        throw new OperatorException(
            "Couldn't retrieve the currently connected namespace. Make sure it's correctly set in your ~/.kube/config file, using, e.g. 'kubectl config set-context <your context> --namespace=<your namespace>'");
      }
      targetNamespaces = Collections.singleton(namespace);
    }
    return targetNamespaces;
  }

  /**
   * Replaces the item store in informer. See underling <a href=
   * "https://github.com/fabric8io/kubernetes-client/blob/43b67939fde91046ab7fb0c362f500c2b46eb59e/kubernetes-client/src/main/java/io/fabric8/kubernetes/client/informers/impl/DefaultSharedIndexInformer.java#L273">
   * method</a> in fabric8 client informer implementation.
   *
   * The main goal, is to be able to use limited caches.
   *
   * See {@link io.javaoperatorsdk.operator.processing.event.source.cache.BoundedItemStore} and
   * <a href=
   * "https://github.com/java-operator-sdk/java-operator-sdk/blob/d6eda0138dba6d93c0ff22a5ffcaa7663fa65ca2/caffein-bounded-cache-support/src/main/java/io/javaoperatorsdk/operator/processing/event/source/cache/CaffeinBoundedCache.java">
   * CaffeinBoundedCache</a>
   *
   * @return Optional ItemStore implementation. If present this item store will be used inside the
   *         informers.
   */
  default Optional<ItemStore<R>> getItemStore() {
    return Optional.empty();
  }
}
