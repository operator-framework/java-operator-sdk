package io.javaoperatorsdk.operator.baseapi.statuscache;

import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AbstractEventSource;
import io.javaoperatorsdk.operator.processing.event.source.IndexerResourceCache;

public class PeriodicTriggerEventSource<P extends HasMetadata>
    extends AbstractEventSource<Void, P> {

  public static final int DEFAULT_PERIOD = 30;
  private final Timer timer = new Timer();
  private final IndexerResourceCache<P> primaryCache;
  private final int period;

  public PeriodicTriggerEventSource(IndexerResourceCache<P> primaryCache) {
    this(primaryCache, DEFAULT_PERIOD);
  }

  public PeriodicTriggerEventSource(IndexerResourceCache<P> primaryCache, int period) {
    super(Void.class);
    this.primaryCache = primaryCache;
    this.period = period;
  }

  @Override
  public Set<Void> getSecondaryResources(P primary) {
    return Set.of();
  }

  @Override
  public void start() throws OperatorException {
    super.start();
    timer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            primaryCache
                .list()
                .forEach(r -> getEventHandler().handleEvent(new Event(ResourceID.fromResource(r))));
          }
        },
        0,
        period);
  }
}
