package io.javaoperatorsdk.operator.processing.event.source.pool;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;

public class InformerPool
    extends AbstractEventSourcePool<InformerClassifier, SharedIndexInformer<?>> {

  private final KubernetesClient client;

  private Map<InformerClassifier, SharedIndexInformer<?>> informers = new ConcurrentHashMap<>();
  private Map<SharedIndexInformer<?>, AtomicInteger> counters = new ConcurrentHashMap<>();

  public InformerPool(KubernetesClient client) {
    this.client = client;
  }

  @Override
  public SharedIndexInformer<?> getEventSource(InformerClassifier classifier) {
    var actual = informers.get(classifier);
    if (actual == null) {
      actual = null; // create Informer
    }
    incrementCounter(actual);
    return null;
  }

  private synchronized void incrementCounter(SharedIndexInformer<?> actual) {
    counters.compute(actual, (k, v) -> new AtomicInteger(v == null ? 0 : v.incrementAndGet()));
  }

  @Override
  public void removeEventSource(SharedIndexInformer<?> informerEventSource) {}
}
