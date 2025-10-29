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
package io.javaoperatorsdk.operator.monitoring.micrometer;

import java.util.Collections;
import java.util.Set;

import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.micrometer.core.instrument.Meter;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultBehaviorIT extends AbstractMicrometerMetricsTestFixture {
  @Override
  protected MicrometerMetrics getMetrics() {
    return MicrometerMetrics.newMicrometerMetricsBuilder(registry).build();
  }

  @Override
  protected Set<Meter.Id> preDeleteChecks(ResourceID resourceID) {
    // no meter should be recorded because we're not tracking anything to be deleted later
    assertThat(metrics.recordedMeterIdsFor(resourceID)).isEmpty();
    // metrics are collected per resource by default for now, this will change in a future release
    assertThat(registry.getMetersAsString()).contains(resourceID.getName());
    return Collections.emptySet();
  }

  @Override
  protected void postDeleteChecks(ResourceID resourceID, Set<Meter.Id> recordedMeters) {
    // meters should be neither recorded, nor removed by default
    assertThat(registry.getRemoved()).isEmpty();
  }
}
