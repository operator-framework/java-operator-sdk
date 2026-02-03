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
package io.javaoperatorsdk.operator.baseapi.performance;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PerformanceTestResult {

  private final Map<String, Object> additionalProperties = new HashMap<>();

  @JsonAnySetter
  public void addProperty(String key, Object value) {
    additionalProperties.put(key, value);
  }

  @JsonAnyGetter
  public Map<String, Object> getProperties() {
    return additionalProperties;
  }

  private List<PerformanceTestSummary> summaries;

  public List<PerformanceTestSummary> getSummaries() {
    return summaries;
  }

  public void setSummaries(List<PerformanceTestSummary> summaries) {
    this.summaries = summaries;
  }
}
