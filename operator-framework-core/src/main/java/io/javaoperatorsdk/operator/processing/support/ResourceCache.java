package io.javaoperatorsdk.operator.processing.support;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class ResourceCache<P extends HasMetadata> {

  private BiPredicate<Pair<P>, P> evictionPredicate;
  private ConcurrentHashMap<ResourceID, Pair<P>> cache = new ConcurrentHashMap<>();

  public ResourceCache(BiPredicate<Pair<P>, P> evictionPredicate) {
    this.evictionPredicate = evictionPredicate;
  }

  public void cacheResource(P beforeUpdate, P afterUpdate) {
    var resourceId = ResourceID.fromResource(beforeUpdate);
    cache.put(resourceId, new Pair<>(beforeUpdate, afterUpdate));
  }

  public P getFreshResource(P newVersion) {
    var resourceId = ResourceID.fromResource(newVersion);
    var pair = cache.get(resourceId);
    if (pair == null) {
      return newVersion;
    }
    if (evictionPredicate.test(pair, newVersion)) {
      cache.remove(resourceId);
      return newVersion;
    } else {
      return pair.afterUpdate();
    }
  }

  public record Pair<T extends HasMetadata>(T beforeUpdate, T afterUpdate) {}

  public static class ResourceVersionComparePredicate<T extends HasMetadata>
      implements BiPredicate<Pair<T>, T> {
    @Override
    public boolean test(Pair<T> updatePair, T newVersion) {
      return Long.parseLong(updatePair.afterUpdate().getMetadata().getResourceVersion())
          <= Long.parseLong(newVersion.getMetadata().getResourceVersion());
    }
  }

  public static class ResourceVersionEqualityPredicate<T extends HasMetadata>
      implements BiPredicate<Pair<T>, T> {
    @Override
    public boolean test(Pair<T> updatePair, T newVersion) {
      return !updatePair.beforeUpdate().equals(newVersion.getMetadata().getResourceVersion());
    }
  }
}
