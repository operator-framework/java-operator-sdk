package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceCache;

import static io.javaoperatorsdk.operator.processing.event.source.controller.ControllerResourceEventSource.ANY_NAMESPACE_MAP_KEY;

public class AggregateResourceCache<T extends HasMetadata, V extends EventSourceWrapper<T>>
    implements ResourceCache<T> {

  private final Map<String, V> sources;

  public AggregateResourceCache(Map<String, V> sources) {
    this.sources = sources;
  }

  @Override
  public Stream<T> list(Predicate<T> predicate) {
    if (predicate == null) {
      return sources.values().stream().flatMap(ResourceCache::list);
    }
    return sources.values().stream().flatMap(i -> i.list(predicate));
  }

  @Override
  public Stream<T> list(String namespace, Predicate<T> predicate) {
    if (isWatchingAllNamespaces()) {
      return getSource(ANY_NAMESPACE_MAP_KEY)
          .map(source -> source.list(namespace, predicate))
          .orElse(Stream.empty());
    } else {
      return getSource(namespace)
          .map(source -> source.list(predicate))
          .orElse(Stream.empty());
    }
  }

  @Override
  public Optional<T> get(ResourceID resourceID) {
    return getSource(resourceID.getNamespace().orElse(ANY_NAMESPACE_MAP_KEY))
        .flatMap(source -> source.get(resourceID));
  }

  private boolean isWatchingAllNamespaces() {
    return sources.containsKey(ANY_NAMESPACE_MAP_KEY);
  }

  Optional<V> getSource(String namespace) {
    namespace = isWatchingAllNamespaces() || namespace == null ? ANY_NAMESPACE_MAP_KEY : namespace;
    return Optional.ofNullable(sources.get(namespace));
  }

}
