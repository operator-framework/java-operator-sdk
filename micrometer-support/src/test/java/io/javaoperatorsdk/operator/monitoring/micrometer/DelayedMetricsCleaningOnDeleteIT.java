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

import java.time.Duration;
import java.util.Set;

import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.micrometer.core.instrument.Meter;

import static org.assertj.core.api.Assertions.assertThat;

public class DelayedMetricsCleaningOnDeleteIT extends AbstractMicrometerMetricsTestFixture {

  private static final int testDelay = 1;

  @Override
  protected MicrometerMetrics getMetrics() {
    return MicrometerMetrics.newPerResourceCollectingMicrometerMetricsBuilder(registry)
        .withCleanUpDelayInSeconds(testDelay)
        .withCleaningThreadNumber(2)
        .build();
  }

  @Override
  protected void postDeleteChecks(ResourceID resourceID, Set<Meter.Id> recordedMeters)
      throws Exception {
    // check that the meters are properly removed after the specified delay
    Thread.sleep(Duration.ofSeconds(testDelay).toMillis());
    assertThat(registry.getRemoved()).isEqualTo(recordedMeters);
    assertThat(metrics.recordedMeterIdsFor(resourceID)).isNull();
  }
}
