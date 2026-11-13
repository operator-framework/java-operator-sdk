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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes performance test results into the directory of the current run:
 *
 * <pre>
 *   &lt;results dir&gt;/&lt;commit timestamp&gt;-&lt;short commit&gt;/run.json
 *   &lt;results dir&gt;/&lt;commit timestamp&gt;-&lt;short commit&gt;/&lt;type&gt;/&lt;test class&gt;.&lt;test method&gt;.json
 * </pre>
 *
 * <p>The results directory defaults to {@code target/performance-results} and can be set with the
 * {@value RunMetadata#RESULTS_DIR_PROPERTY} system property. CI collects those directories from all
 * performance test jobs and merges them onto the results branch, which is why the layout below the
 * results directory is the same as the one on that branch.
 */
public class ResultsWriter {

  public static final String RUN_FILE = "run.json";
  private static final String DEFAULT_RESULTS_DIR = "target/performance-results";

  private static final Logger log = LoggerFactory.getLogger(ResultsWriter.class);

  private final RunMetadata run;
  private final Path runDirectory;

  public ResultsWriter() {
    this(defaultResultsDirectory(), RunMetadata.current());
  }

  public ResultsWriter(Path resultsDirectory, RunMetadata run) {
    this.run = run;
    this.runDirectory = resultsDirectory.resolve(run.directoryName());
  }

  public static Path defaultResultsDirectory() {
    return Path.of(System.getProperty(RunMetadata.RESULTS_DIR_PROPERTY, DEFAULT_RESULTS_DIR));
  }

  public RunMetadata run() {
    return run;
  }

  public Path runDirectory() {
    return runDirectory;
  }

  /** Writes a result file, creating {@value #RUN_FILE} for the run if it is not there yet. */
  public Path write(TestResult result) {
    try {
      var runFile = runDirectory.resolve(RUN_FILE);
      if (!Files.exists(runFile)) {
        Json.write(runFile, run);
      }
      var resultFile = runDirectory.resolve(result.fileName());
      Json.write(resultFile, result);
      log.info("Recorded {} result of {} to {}", result.type(), result.testMethod(), resultFile);
      return resultFile;
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot write results to " + runDirectory, e);
    }
  }
}
