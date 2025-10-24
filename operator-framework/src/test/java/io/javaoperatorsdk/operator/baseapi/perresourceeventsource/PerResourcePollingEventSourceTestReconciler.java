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
package io.javaoperatorsdk.operator.baseapi.perresourceeventsource;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.ResourceIDMapper;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingConfigurationBuilder;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource;

@ControllerConfiguration
public class PerResourcePollingEventSourceTestReconciler
    implements Reconciler<PerResourceEventSourceCustomResource> {

  public static final int POLL_PERIOD = 100;
  private final Map<String, Integer> numberOfExecutions = new ConcurrentHashMap<>();
  private final Map<String, Integer> numberOfFetchExecutions = new ConcurrentHashMap<>();

  @Override
  public UpdateControl<PerResourceEventSourceCustomResource> reconcile(
      PerResourceEventSourceCustomResource resource,
      Context<PerResourceEventSourceCustomResource> context)
      throws Exception {
    numberOfExecutions.putIfAbsent(resource.getMetadata().getName(), 0);
    numberOfExecutions.compute(resource.getMetadata().getName(), (s, v) -> v + 1);
    return UpdateControl.noUpdate();
  }

  @Override
  public List<EventSource<?, PerResourceEventSourceCustomResource>> prepareEventSources(
      EventSourceContext<PerResourceEventSourceCustomResource> context) {
    PerResourcePollingEventSource<String, PerResourceEventSourceCustomResource, String>
        eventSource =
            new PerResourcePollingEventSource<>(
                String.class,
                context,
                new PerResourcePollingConfigurationBuilder<
                        String, PerResourceEventSourceCustomResource, String>(
                        (PerResourceEventSourceCustomResource resource) -> {
                          numberOfFetchExecutions.putIfAbsent(resource.getMetadata().getName(), 0);
                          numberOfFetchExecutions.compute(
                              resource.getMetadata().getName(), (s, v) -> v + 1);
                          return Set.of(UUID.randomUUID().toString());
                        },
                        Duration.ofMillis(POLL_PERIOD))
                    .withResourceIDMapper(ResourceIDMapper.singleResourceResourceIDMapper())
                    .build());
    return List.of(eventSource);
  }

  public int getNumberOfExecutions(String name) {
    var num = numberOfExecutions.get(name);
    return num == null ? 0 : num;
  }

  public int getNumberOfFetchExecution(String name) {
    return numberOfFetchExecutions.get(name);
  }
}
