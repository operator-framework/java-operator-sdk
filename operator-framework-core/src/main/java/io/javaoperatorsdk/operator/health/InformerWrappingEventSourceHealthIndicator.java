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
package io.javaoperatorsdk.operator.health;

import java.util.Map;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface InformerWrappingEventSourceHealthIndicator<R extends HasMetadata>
    extends EventSourceHealthIndicator {

  Map<String, InformerHealthIndicator> informerHealthIndicators();

  @Override
  default Status getStatus() {
    var nonUp =
        informerHealthIndicators().values().stream()
            .filter(i -> i.getStatus() != Status.HEALTHY)
            .findAny();

    return nonUp.isPresent() ? Status.UNHEALTHY : Status.HEALTHY;
  }
}
