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
package io.javaoperatorsdk.operator.api.reconciler;

import java.util.*;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

public class EventSourceUtils {

  @SuppressWarnings("unchecked")
  public static <P extends HasMetadata> List<EventSource<?, P>> dependentEventSources(
      EventSourceContext<P> eventSourceContext, DependentResource... dependentResources) {
    return Arrays.stream(dependentResources)
        .flatMap(dr -> dr.eventSource(eventSourceContext).stream())
        .toList();
  }

  @SuppressWarnings("unchecked")
  public static <P extends HasMetadata> List<EventSource<?, P>> eventSourcesFromWorkflow(
      EventSourceContext<P> context, Workflow<P> workflow) {
    return workflow.getDependentResourcesWithoutActivationCondition().stream()
        .flatMap(dr -> dr.eventSource(context).stream())
        .toList();
  }
}
