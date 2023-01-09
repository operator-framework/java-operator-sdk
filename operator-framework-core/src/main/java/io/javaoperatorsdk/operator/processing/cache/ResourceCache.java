package io.javaoperatorsdk.operator.processing.cache;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class ResourceCache<R> implements Cache<R> {

  private ResourceFetcher<R> resourceFetcher;
  private ExistenceCheckHandler existenceCheckHandler;
  private Cache<R> boundedCache;

  @Override
  public Optional<R> get(ResourceID resourceID) {


    return Optional.empty();
  }

  @Override
  public Stream<ResourceID> keys() {
    return null;
  }

  @Override
  public Stream<R> list(Predicate<R> predicate) {
    return null;
  }
}
