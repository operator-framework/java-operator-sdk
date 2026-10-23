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
package io.javaoperatorsdk.operator.performance;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.performance.results.PerformanceTest;
import io.javaoperatorsdk.operator.performance.results.PerformanceTestResults;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Measures how fast a locally run operator reconciles a batch of custom resources against a real
 * cluster. The reconciler itself does close to nothing, so what is measured is the SDK event
 * processing plus the API server round trips.
 *
 * <p>The scenario is tunable with system properties:
 *
 * <ul>
 *   <li>{@code performance.resourceCount} - number of custom resources to create (default 100)
 *   <li>{@code performance.timeoutSeconds} - how long to wait for all reconciliations (default 120)
 * </ul>
 */
@PerformanceTest(type = PerformanceTest.END_TO_END)
class ReconciliationThroughputE2E {

  private static final Logger log = LoggerFactory.getLogger(ReconciliationThroughputE2E.class);

  private static final String RESOURCE_NAME_PREFIX = "perf-resource-";
  private static final String INITIAL_VALUE = "initial";
  private static final String UPDATED_VALUE = "updated";

  private static final int RESOURCE_COUNT = Integer.getInteger("performance.resourceCount", 100);
  private static final Duration TIMEOUT =
      Duration.ofSeconds(Integer.getInteger("performance.timeoutSeconds", 120));

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(new PerformanceReconciler()).build();

  @Test
  void reconcilesBatchOfResourcesOnCreateAndUpdate(PerformanceTestResults results) {
    results.param("resourceCount", RESOURCE_COUNT);

    log.info("Creating {} resources", RESOURCE_COUNT);
    var start = System.nanoTime();
    for (int i = 0; i < RESOURCE_COUNT; i++) {
      operator.create(resource(i, INITIAL_VALUE));
    }
    var createdAt = System.nanoTime();
    awaitAllObserved(INITIAL_VALUE);
    report(results, "create", start, createdAt, System.nanoTime());

    log.info("Updating {} resources", RESOURCE_COUNT);
    start = System.nanoTime();
    for (var resource : operator.resources(PerformanceCustomResource.class).list().getItems()) {
      resource.getSpec().setValue(UPDATED_VALUE);
      operator.update(resource);
    }
    var updatedAt = System.nanoTime();
    awaitAllObserved(UPDATED_VALUE);
    report(results, "update", start, updatedAt, System.nanoTime());

    var executions =
        operator.getReconcilerOfType(PerformanceReconciler.class).getNumberOfExecutions();
    log.info(
        "Total reconciliations: {} for {} resources ({} per resource)",
        executions,
        RESOURCE_COUNT,
        String.format("%.2f", executions / (double) RESOURCE_COUNT));
    results.record("reconciliationsPerResource", executions / (double) RESOURCE_COUNT, "count");

    // one reconciliation for the create, one for the update; more means redundant work
    assertThat(executions).isGreaterThanOrEqualTo(2 * RESOURCE_COUNT);
  }

  private void awaitAllObserved(String expectedValue) {
    await()
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(() -> assertThat(numberOfObserved(expectedValue)).isEqualTo(RESOURCE_COUNT));
  }

  private long numberOfObserved(String expectedValue) {
    return operator.resources(PerformanceCustomResource.class).list().getItems().stream()
        .filter(r -> r.getStatus() != null)
        .filter(r -> expectedValue.equals(r.getStatus().getObservedValue()))
        .filter(r -> r.getMetadata().getGeneration().equals(r.getStatus().getObservedGeneration()))
        .count();
  }

  private void report(
      PerformanceTestResults results, String phase, long start, long submitted, long end) {
    var submitMs = (submitted - start) / 1_000_000;
    var totalMs = (end - start) / 1_000_000;
    log.info(
        "{}: submitted {} resources in {} ms, all reconciled after {} ms ({} reconciliations/sec)",
        phase,
        RESOURCE_COUNT,
        submitMs,
        totalMs,
        String.format("%.1f", RESOURCE_COUNT * 1000.0 / Math.max(totalMs, 1)));

    results
        .recordElapsed(phase + ".submit", start, submitted)
        .recordElapsed(phase, start, end)
        .record(
            phase + ".throughput",
            RESOURCE_COUNT * 1_000_000_000.0 / (end - start),
            "reconciliations/s");
  }

  private PerformanceCustomResource resource(int index, String value) {
    var resource = new PerformanceCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME_PREFIX + index).build());
    var spec = new PerformanceCustomResourceSpec();
    spec.setValue(value);
    resource.setSpec(spec);
    return resource;
  }
}
