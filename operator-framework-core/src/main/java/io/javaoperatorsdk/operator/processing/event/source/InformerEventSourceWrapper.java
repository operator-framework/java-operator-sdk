package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.Cloner;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

class InformerEventSourceWrapper<T extends HasMetadata> implements EventSourceWrapper<T> {

  private final SharedIndexInformer<T> informer;
  private final InformerResourceCache<T> cache;

  InformerEventSourceWrapper(SharedIndexInformer<T> informer, Cloner cloner,
      ResourceEventHandler<T> parent) {
    this.informer = informer;
    this.informer.addEventHandler(parent);
    this.cache = new InformerResourceCache<>(informer, cloner);
  }

  @Override
  public void start() throws OperatorException {
    informer.run();
  }

  @Override
  public void stop() throws OperatorException {
    informer.stop();
  }

  @Override
  public Optional<T> get(ResourceID resourceID) {
    return cache.get(resourceID);
  }

  @Override
  public Stream<T> list(Predicate<T> predicate) {
    return cache.list(predicate);
  }

  @Override
  public Stream<T> list(String namespace, Predicate<T> predicate) {
    return cache.list(namespace, predicate);
  }
}
