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
package io.javaoperatorsdk.operator.sample.metrics;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.javaoperatorsdk.operator.junit.AbstractOperatorExtension;
import io.javaoperatorsdk.operator.junit.ClusterDeployedOperatorExtension;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.metrics.customresource.MetricsHandlingCustomResource1;
import io.javaoperatorsdk.operator.sample.metrics.customresource.MetricsHandlingCustomResource2;
import io.javaoperatorsdk.operator.sample.metrics.customresource.MetricsHandlingSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MetricsHandlingE2E {
  static final Logger log = LoggerFactory.getLogger(MetricsHandlingE2E.class);
  static final String OBSERVABILITY_NAMESPACE = "observability";
  static final int PROMETHEUS_PORT = 9090;
  private LocalPortForward prometheusPortForward;

  MetricsHandlingE2E() throws FileNotFoundException {}

  boolean isLocal() {
    String deployment = System.getProperty("test.deployment");
    boolean remote = (deployment != null && deployment.equals("remote"));
    log.info("Running the operator {} ", remote ? "remotely" : "locally");
    return !remote;
  }

  @RegisterExtension
  AbstractOperatorExtension operator =
      isLocal()
          ? LocallyRunOperatorExtension.builder()
              .withReconciler(new MetricsHandlingReconciler1())
              .withReconciler(new MetricsHandlingReconciler2())
              .build()
          : ClusterDeployedOperatorExtension.builder()
              .withOperatorDeployment(
                  operator()
                      .getKubernetesClient()
                      .load(new FileInputStream("k8s/operator.yaml"))
                      .items())
              .build();

  @BeforeAll
  void setupObservability() {
      log.info("Setting up observability stack...");
      try {
//        // Find the observability script relative to project root
//        File projectRoot = new File(".").getCanonicalFile();
//        while (projectRoot != null && !new File(projectRoot, "observability").exists()) {
//          projectRoot = projectRoot.getParentFile();
//        }
//
//        if (projectRoot == null) {
//          throw new IllegalStateException("Could not find observability directory");
//        }
//
//        File scriptFile = new File(projectRoot, "observability/install-observability.sh");
//        if (!scriptFile.exists()) {
//          throw new IllegalStateException("Observability script not found at: " + scriptFile.getAbsolutePath());
//        }
//
//        log.info("Running observability setup script: {}", scriptFile.getAbsolutePath());
//
//        // Run the install-observability.sh script
//        ProcessBuilder processBuilder =
//                new ProcessBuilder("/bin/sh", scriptFile.getAbsolutePath());
//        processBuilder.redirectErrorStream(true);
//
//        processBuilder.environment().putAll(System.getenv());
//        Process process = processBuilder.start();
//        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
//        String line;
//        while ((line = reader.readLine()) != null) {
//          log.info("Observability setup: {}", line);
//        }
//
//        int exitCode = process.waitFor();
//        if (exitCode != 0) {
//          log.warn("Observability setup script returned exit code: {}", exitCode);
//        }

        // Wait for Prometheus to be ready
        await()
                .atMost(Duration.ofMinutes(3))
                .pollInterval(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> {
                          var prometheusPod =
                                  operator
                                          .getKubernetesClient()
                                          .pods()
                                          .inNamespace(OBSERVABILITY_NAMESPACE)
                                          .withLabel("app.kubernetes.io/name", "prometheus")
                                          .list()
                                          .getItems()
                                          .stream()
                                          .findFirst();
                          assertThat(prometheusPod).isPresent();
                          assertThat(prometheusPod.get().getStatus().getPhase()).isEqualTo("Running");
                        });

        log.info("Observability stack is ready");

        // Setup port forwarding to Prometheus
        setupPrometheusPortForward();

      } catch (Exception e) {
        log.error("Failed to setup observability stack", e);
        throw new RuntimeException(e);
      }


  }
  private void setupPrometheusPortForward() {
    try {
      Pod prometheusPod =
          operator
              .getKubernetesClient()
              .pods()
              .inNamespace(OBSERVABILITY_NAMESPACE)
              .withLabel("app.kubernetes.io/name", "prometheus")
              .list()
              .getItems()
              .stream()
              .findFirst()
              .orElseThrow(() -> new IllegalStateException("Prometheus pod not found"));

      log.info(
          "Setting up port forward to Prometheus pod: {}", prometheusPod.getMetadata().getName());
      prometheusPortForward =
          operator
              .getKubernetesClient()
              .pods()
              .inNamespace(OBSERVABILITY_NAMESPACE)
              .withName(prometheusPod.getMetadata().getName())
              .portForward(PROMETHEUS_PORT);

      log.info(
          "Prometheus port forward established on local port: {}",
          prometheusPortForward.getLocalPort());

      // Wait a bit for port forward to be ready
      Thread.sleep(2000);

    } catch (Exception e) {
      log.error("Failed to setup Prometheus port forward", e);
      throw new RuntimeException(e);
    }
  }

  @AfterAll
  void cleanup() {
    if (prometheusPortForward != null) {
      try {
        prometheusPortForward.close();
        log.info("Closed Prometheus port forward");
      } catch (IOException e) {
        log.warn("Failed to close Prometheus port forward", e);
      }
    }
  }

  @Test
  void testPropagatedMetrics() throws Exception {
    log.info("Starting metrics propagation test");

    // Create successful resources
    MetricsHandlingCustomResource1 successResource1 = createResource1("test-success-1", 42);
    operator.create(successResource1);

    MetricsHandlingCustomResource2 successResource2 = createResource2("test-success-2", 77);
    operator.create(successResource2);

    // Create resources that will fail
    MetricsHandlingCustomResource1 failResource1 = createResource1("test-fail-1", 100);
    operator.create(failResource1);

    MetricsHandlingCustomResource2 errorResource2 = createResource2("test-error-2", 200);
    operator.create(errorResource2);

    // Wait for reconciliations to happen multiple times
    log.info("Waiting for reconciliations to occur...");
    Thread.sleep(10000);

    if (!isLocal()) {
      // Query Prometheus to verify metrics
      verifyPrometheusMetrics();
    } else {
      log.info("Skipping Prometheus verification for local test");
      // For local tests, just verify that resources exist
      await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                var resource = operator.get(MetricsHandlingCustomResource1.class, "test-success-1");
                assertThat(resource).isNotNull();
                assertThat(resource.getStatus()).isNotNull();
                assertThat(resource.getStatus().getObservedNumber()).isEqualTo(42);
              });
    }

    log.info("Metrics propagation test completed");
  }

  private void verifyPrometheusMetrics() throws Exception {
    log.info("Verifying metrics in Prometheus...");

    int localPort = prometheusPortForward.getLocalPort();
    String prometheusUrl = "http://localhost:" + localPort;

    // Verify reconciliation started metrics
    String startedQuery = "operator_sdk_reconciliations_started_total";
    await()
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              String result = queryPrometheus(prometheusUrl, startedQuery);
              log.info("Reconciliations started metric: {}", result);
              assertThat(result).contains("\"status\":\"success\"");
              assertThat(result).contains("operator_sdk_reconciliations_started_total");
            });

    // Verify success metrics
    String successQuery = "operator_sdk_reconciliations_success_total";
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              String result = queryPrometheus(prometheusUrl, successQuery);
              log.info("Reconciliations success metric: {}", result);
              assertThat(result).contains("\"status\":\"success\"");
              assertThat(result).contains("operator_sdk_reconciliations_success_total");
            });

    // Verify failure metrics
    String failureQuery = "operator_sdk_reconciliations_failure_total";
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              String result = queryPrometheus(prometheusUrl, failureQuery);
              log.info("Reconciliations failure metric: {}", result);
              assertThat(result).contains("\"status\":\"success\"");
              assertThat(result).contains("operator_sdk_reconciliations_failure_total");
            });

    // Verify controller execution metrics
    String controllerQuery = "operator_sdk_controllers_success_total";
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              String result = queryPrometheus(prometheusUrl, controllerQuery);
              log.info("Controller success metric: {}", result);
              assertThat(result).contains("\"status\":\"success\"");
            });

    // Verify execution time metrics
    String executionTimeQuery = "operator_sdk_reconciliations_execution_seconds_count";
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              String result = queryPrometheus(prometheusUrl, executionTimeQuery);
              log.info("Execution time metric: {}", result);
              assertThat(result).contains("\"status\":\"success\"");
            });

    log.info("All metrics verified successfully in Prometheus");
  }

  private String queryPrometheus(String prometheusUrl, String query) throws IOException {
    String urlString = prometheusUrl + "/api/v1/query?query=" + query;
    URL url = new URL(urlString);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(5000);

    int responseCode = connection.getResponseCode();
    if (responseCode != 200) {
      throw new IOException("Prometheus query failed with response code: " + responseCode);
    }

    try (BufferedReader in =
        new BufferedReader(
            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
      StringBuilder response = new StringBuilder();
      String line;
      while ((line = in.readLine()) != null) {
        response.append(line);
      }
      return response.toString();
    }
  }

  private MetricsHandlingCustomResource1 createResource1(String name, int number) {
    MetricsHandlingCustomResource1 resource = new MetricsHandlingCustomResource1();
    resource.getMetadata().setName(name);

    MetricsHandlingSpec spec = new MetricsHandlingSpec();
    spec.setObservedNumber(number);
    resource.setSpec(spec);

    return resource;
  }

  private MetricsHandlingCustomResource2 createResource2(String name, int number) {
    MetricsHandlingCustomResource2 resource = new MetricsHandlingCustomResource2();
    resource.getMetadata().setName(name);

    MetricsHandlingSpec spec = new MetricsHandlingSpec();
    spec.setObservedNumber(number);
    resource.setSpec(spec);

    return resource;
  }

  AbstractOperatorExtension operator() {
    return operator;
  }
}
