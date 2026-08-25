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

import java.util.ArrayList;
import java.util.Objects;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Records the measurements of tests annotated with {@link PerformanceTest}. Registered by that
 * annotation, so it is not meant to be used with {@code @ExtendWith} directly.
 *
 * <p>Results of failed tests are not recorded, a partial measurement would only distort the
 * comparison with other runs.
 */
public class PerformanceResultsExtension
    implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

  private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(PerformanceResultsExtension.class);
  private static final String RESULTS = "results";
  private static final String START_TIME = "startTime";
  private static final String TOTAL_MEASUREMENT = "total";

  private final ResultsWriter writer;

  public PerformanceResultsExtension() {
    this(new ResultsWriter());
  }

  PerformanceResultsExtension(ResultsWriter writer) {
    this.writer = writer;
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    var store = context.getStore(NAMESPACE);
    store.put(RESULTS, new PerformanceTestResults());
    store.put(START_TIME, System.nanoTime());
  }

  @Override
  public void afterEach(ExtensionContext context) {
    var endTime = System.nanoTime();
    if (context.getExecutionException().isPresent()) {
      return;
    }
    var store = context.getStore(NAMESPACE);
    var results = Objects.requireNonNull(store.get(RESULTS, PerformanceTestResults.class));
    var startTime = Objects.requireNonNull(store.get(START_TIME, Long.class));
    var measurements = new ArrayList<>(results.measurements());
    measurements.add(
        new Measurement(
            TOTAL_MEASUREMENT, (endTime - startTime) / 1_000_000.0, Measurement.MILLISECONDS));

    writer.write(
        TestResult.of(
            typeOf(context),
            context.getRequiredTestClass().getName(),
            context.getRequiredTestMethod().getName(),
            writer.run(),
            measurements));
  }

  @Override
  public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext context) {
    return PerformanceTestResults.class.equals(parameterContext.getParameter().getType());
  }

  @Override
  public Object resolveParameter(ParameterContext parameterContext, ExtensionContext context) {
    return context.getStore(NAMESPACE).get(RESULTS, PerformanceTestResults.class);
  }

  private static String typeOf(ExtensionContext context) {
    var testClass = context.getRequiredTestClass();
    var annotation = testClass.getAnnotation(PerformanceTest.class);
    if (annotation == null) {
      throw new IllegalStateException(
          testClass.getName() + " is not annotated with @" + PerformanceTest.class.getSimpleName());
    }
    return annotation.type();
  }
}
