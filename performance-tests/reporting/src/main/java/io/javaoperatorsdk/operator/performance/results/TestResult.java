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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * All measurements of a single performance test, the content of one result file.
 *
 * @param type category of the performance test, see {@link PerformanceTest#type()}
 * @param testClass fully qualified name of the test, for JMH the benchmark class
 * @param testMethod test or benchmark method the measurements belong to
 * @param commitAbbrev repeated from {@code run.json} so a single file is self describing
 * @param commitTimestamp repeated from {@code run.json} so a single file is self describing
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TestResult(
    String type,
    String testClass,
    String testMethod,
    String commitAbbrev,
    String commitTimestamp,
    List<Measurement> measurements) {

  public static TestResult of(
      String type,
      String testClass,
      String testMethod,
      RunMetadata run,
      List<Measurement> measurements) {
    return new TestResult(
        type, testClass, testMethod, run.commitAbbrev(), run.commitTimestamp(), measurements);
  }

  /** Name of the result file, relative to the directory of the run. */
  @JsonIgnore
  public String fileName() {
    return type + "/" + testClass + "." + testMethod + ".json";
  }
}
