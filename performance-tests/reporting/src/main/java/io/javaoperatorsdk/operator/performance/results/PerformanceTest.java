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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks a test class as a performance test, its measurements are recorded by {@link
 * PerformanceResultsExtension}. Test methods can take a {@link PerformanceTestResults} parameter to
 * add measurements, the duration of the test method itself is always recorded.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(PerformanceResultsExtension.class)
public @interface PerformanceTest {

  /** JMH benchmarks. */
  String JMH = "jmh";

  /** Tests measuring the SDK in process, without a cluster. */
  String IN_PROCESS = "in-process";

  /** Tests measuring an operator running against a real cluster. */
  String END_TO_END = "e2e";

  /**
   * Category of the performance test, becomes the directory the results are stored in. Use one of
   * the constants of this annotation unless a new category is added.
   */
  String type();
}
