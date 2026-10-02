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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ResultsWriterTest {

  private static final RunMetadata RUN =
      new RunMetadata(
          "abc123def456", "abc123d", "2026-08-25T12:00:00Z", "main", "2026-08-25T13:00:00Z");

  @Test
  void storesResultsUnderADirectoryNamedAfterTheCommit(@TempDir Path resultsDirectory) {
    var writer = new ResultsWriter(resultsDirectory, RUN);

    writer.write(
        TestResult.of(
            PerformanceTest.END_TO_END,
            "com.acme.ThroughputE2E",
            "measuresThroughput",
            RUN,
            List.of(new Measurement("create", 12.5, "ms", Map.of("resourceCount", "100")))));

    var runDirectory = resultsDirectory.resolve("20260825T120000Z-abc123d");
    assertThat(runDirectory.resolve(ResultsWriter.RUN_FILE)).exists();
    assertThat(runDirectory.resolve("e2e/com.acme.ThroughputE2E.measuresThroughput.json")).exists();
  }

  @Test
  void indexListsRunsOldestFirstWithTheirTypes(@TempDir Path resultsDirectory) throws IOException {
    var older =
        new RunMetadata("1", "aaaaaaa", "2026-08-24T10:00:00Z", "main", "2026-08-26T10:00:00Z");
    var newer =
        new RunMetadata("2", "bbbbbbb", "2026-08-25T10:00:00Z", "next", "2026-08-25T10:00:00Z");
    // written newest first on purpose, the index has to order by commit, not by insertion
    write(resultsDirectory, newer, PerformanceTest.JMH);
    write(resultsDirectory, older, PerformanceTest.END_TO_END);
    write(resultsDirectory, older, PerformanceTest.IN_PROCESS);

    var index = ResultsIndexer.index(resultsDirectory);

    assertThat(index.runs())
        .map(ResultsIndex.Run::commitAbbrev)
        .containsExactly("aaaaaaa", "bbbbbbb");
    assertThat(index.runs().get(0).types()).containsExactly("e2e", "in-process");
    assertThat(index.runs().get(0).branch()).isEqualTo("main");
    assertThat(index.runs().get(1).types()).containsExactly("jmh");
  }

  @Test
  void indexIgnoresDirectoriesWithoutRunMetadata(@TempDir Path resultsDirectory)
      throws IOException {
    write(resultsDirectory, RUN, PerformanceTest.JMH);
    java.nio.file.Files.createDirectory(resultsDirectory.resolve("not-a-run"));

    assertThat(ResultsIndexer.index(resultsDirectory).runs()).hasSize(1);
  }

  private static void write(Path resultsDirectory, RunMetadata run, String type) {
    new ResultsWriter(resultsDirectory, run)
        .write(
            TestResult.of(
                type,
                "com.acme.SomeTest",
                "someMeasurement",
                run,
                List.of(new Measurement("total", 1.0, "ms"))));
  }
}
