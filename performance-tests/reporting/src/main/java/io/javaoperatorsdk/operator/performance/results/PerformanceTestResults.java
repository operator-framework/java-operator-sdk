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
package io.javaoperatorsdk.operator.performance.results;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects the measurements of a single test method. Injected as a test method parameter by {@link
 * PerformanceResultsExtension}.
 */
public class PerformanceTestResults {

  private final List<Measurement> measurements = new ArrayList<>();
  private final Map<String, String> params = new LinkedHashMap<>();

  /**
   * Adds a parameter describing the scenario, for example the number of resources. It is attached
   * to every measurement recorded afterwards, so that results are only compared with results of the
   * same scenario.
   */
  public PerformanceTestResults param(String name, Object value) {
    params.put(name, String.valueOf(value));
    return this;
  }

  public PerformanceTestResults record(String name, double value, String unit) {
    measurements.add(new Measurement(name, value, unit, Map.copyOf(params)));
    return this;
  }

  public PerformanceTestResults recordDuration(String name, Duration duration) {
    return recordMillis(name, duration.toNanos() / 1_000_000.0);
  }

  /** Records the duration between two {@link System#nanoTime()} readings. */
  public PerformanceTestResults recordElapsed(String name, long startNanos, long endNanos) {
    return recordMillis(name, (endNanos - startNanos) / 1_000_000.0);
  }

  public PerformanceTestResults recordMillis(String name, double millis) {
    return record(name, millis, Measurement.MILLISECONDS);
  }

  public List<Measurement> measurements() {
    return List.copyOf(measurements);
  }
}
