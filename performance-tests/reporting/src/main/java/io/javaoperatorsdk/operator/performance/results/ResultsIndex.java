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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Content of {@value ResultsIndexer#INDEX_FILE}: all runs stored on the results branch, ordered
 * from oldest to newest commit. Lets tooling read the results in order without looking at the git
 * history of the branch they are stored on.
 *
 * <p>Holds nothing that changes between two regenerations of the same runs, so that re-indexing
 * without new results does not produce a commit.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResultsIndex(List<Run> runs) {

  /**
   * @param directory name of the directory the results of the run are in
   * @param types the categories of performance tests that produced results in this run
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Run(
      String directory,
      String commit,
      String commitAbbrev,
      String commitTimestamp,
      String branch,
      List<String> types) {}
}
