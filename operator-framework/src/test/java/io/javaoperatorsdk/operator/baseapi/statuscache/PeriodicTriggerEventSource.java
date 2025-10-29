/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
