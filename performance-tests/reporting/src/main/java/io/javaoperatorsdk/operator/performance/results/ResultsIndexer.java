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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Regenerates {@value #INDEX_FILE} for a results directory by scanning the run directories in it.
 * Rebuilding instead of appending keeps the index correct no matter in which order runs are added,
 * and avoids conflicts when several jobs contribute results for the same commit.
 *
 * <p>Invoked by CI after the results of a commit have been merged onto the results branch:
 *
 * <pre>
 * java -cp performance-results-tools.jar \
 *   io.javaoperatorsdk.operator.performance.results.ResultsIndexer performance-tests/results
 * </pre>
 */
public final class ResultsIndexer {

  public static final String INDEX_FILE = "index.json";

  private ResultsIndexer() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      System.err.println("Usage: " + ResultsIndexer.class.getName() + " <results directory>");
      System.exit(1);
      return;
    }
    var resultsDirectory = Path.of(args[0]);
    var index = index(resultsDirectory);
    var indexFile = resultsDirectory.resolve(INDEX_FILE);
    Json.write(indexFile, index);
    System.out.println("Indexed " + index.runs().size() + " run(s) into " + indexFile);
  }

  /** Reads all runs of a results directory, ordered from the oldest to the newest commit. */
  public static ResultsIndex index(Path resultsDirectory) throws IOException {
    var runs = new ArrayList<ResultsIndex.Run>();
    try (var directories = Files.list(resultsDirectory)) {
      for (var directory : directories.filter(Files::isDirectory).toList()) {
        var run = read(directory);
        if (run != null) {
          runs.add(run);
        }
      }
    }
    runs.sort(
        Comparator.comparing(ResultsIndex.Run::commitTimestamp)
            .thenComparing(ResultsIndex.Run::directory));
    return new ResultsIndex(runs);
  }

  private static ResultsIndex.Run read(Path directory) throws IOException {
    var runFile = directory.resolve(ResultsWriter.RUN_FILE);
    if (!Files.isRegularFile(runFile)) {
      return null;
    }
    var metadata = Json.read(runFile, RunMetadata.class);
    return new ResultsIndex.Run(
        String.valueOf(directory.getFileName()),
        metadata.commit(),
        metadata.commitAbbrev(),
        metadata.commitTimestamp(),
        metadata.branch(),
        types(directory));
  }

  private static List<String> types(Path directory) throws IOException {
    try (Stream<Path> content = Files.list(directory)) {
      return content
          .filter(Files::isDirectory)
          .map(path -> String.valueOf(path.getFileName()))
          .sorted()
          .toList();
    }
  }
}
