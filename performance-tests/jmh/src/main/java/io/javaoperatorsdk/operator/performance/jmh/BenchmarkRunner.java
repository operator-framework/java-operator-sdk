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
package io.javaoperatorsdk.operator.performance.jmh;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.CommandLineOptions;

import io.javaoperatorsdk.operator.performance.results.Measurement;
import io.javaoperatorsdk.operator.performance.results.PerformanceTest;
import io.javaoperatorsdk.operator.performance.results.ResultsWriter;
import io.javaoperatorsdk.operator.performance.results.TestResult;

/**
 * Main class of the benchmark jar. Takes the same arguments as {@link org.openjdk.jmh.Main} and in
 * addition records the scores as performance test results, so that they can be stored and compared
 * per commit.
 *
 * <p>The scores are read from the objects the runner returns, not from the JMH result file, so
 * whatever {@code -rf} / {@code -rff} produce stays exactly what JMH itself writes.
 */
public class BenchmarkRunner {

  private static final String NO_PARAMS_MEASUREMENT = "score";

  public static void main(String[] args) throws Exception {
    var options = new CommandLineOptions(args);
    if (options.shouldHelp()
        || options.shouldList()
        || options.shouldListWithParams()
        || options.shouldListResultFormats()
        || options.shouldListProfilers()) {
      org.openjdk.jmh.Main.main(args);
      return;
    }
    record(new Runner(options).run());
  }

  private static void record(Collection<RunResult> runResults) {
    var writer = new ResultsWriter();
    // a benchmark method has one result per parameter combination, all of them end up as
    // measurements in the result file of that method
    Map<String, List<Measurement>> measurementsByBenchmark = new LinkedHashMap<>();
    for (RunResult runResult : runResults) {
      var primary = runResult.getPrimaryResult();
      var params = parameters(runResult);
      measurementsByBenchmark
          .computeIfAbsent(runResult.getParams().getBenchmark(), benchmark -> new ArrayList<>())
          .add(
              new Measurement(
                  measurementName(params), primary.getScore(), primary.getScoreUnit(), params));
    }
    measurementsByBenchmark.forEach(
        (benchmark, measurements) -> {
          var lastDot = benchmark.lastIndexOf('.');
          writer.write(
              TestResult.of(
                  PerformanceTest.JMH,
                  benchmark.substring(0, lastDot),
                  benchmark.substring(lastDot + 1),
                  writer.run(),
                  measurements));
        });
  }

  private static Map<String, String> parameters(RunResult runResult) {
    var benchmarkParams = runResult.getParams();
    Map<String, String> params = new TreeMap<>();
    for (Object key : benchmarkParams.getParamsKeys()) {
      var name = String.valueOf(key);
      params.put(name, benchmarkParams.getParam(name));
    }
    return params;
  }

  private static String measurementName(Map<String, String> params) {
    if (params.isEmpty()) {
      return NO_PARAMS_MEASUREMENT;
    }
    return params.entrySet().stream()
        .map(param -> param.getKey() + "=" + param.getValue())
        .collect(Collectors.joining(","));
  }
}
