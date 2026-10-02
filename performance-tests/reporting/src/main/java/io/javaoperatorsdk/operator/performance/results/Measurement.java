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

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single measured value of a performance test.
 *
 * @param name identifies the measurement within a test, e.g. {@code create} or {@code total}
 * @param value the measured value
 * @param unit unit of the value, e.g. {@code ms} or {@code ops/s}
 * @param params what the measurement was parameterized with, e.g. {@code resourceCount=100}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Measurement(String name, double value, String unit, Map<String, String> params) {

  public static final String MILLISECONDS = "ms";

  public Measurement(String name, double value, String unit) {
    this(name, value, unit, Map.of());
  }
}
