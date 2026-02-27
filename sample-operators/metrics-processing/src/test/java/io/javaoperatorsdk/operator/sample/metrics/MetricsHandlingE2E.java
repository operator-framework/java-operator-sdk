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
import java.util.ArrayDeque;
import java.util.Deque;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.javaoperatorsdk.operator.junit.AbstractOperatorExtension;
import io.javaoperatorsdk.operator.junit.ClusterDeployedOperatorExtension;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.metrics.customresource.MetricsHandlingCustomResource1;
import io.javaoperatorsdk.operator.sample.metrics.customresource.MetricsHandlingCustomResource2;
import io.javaoperatorsdk.operator.sample.metrics.customresource.MetricsHandlingSpec;

import static io.javaoperatorsdk.operator.sample.metrics.MetricsHandlingSampleOperator.isLocal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MetricsHandlingE2E {

  static final Logger log = LoggerFactory.getLogger(MetricsHandlingE2E.class);
  static final String OBSERVABILITY_NAMESPACE = "observability";
  static final int PROMETHEUS_PORT = 9090;
  static final int GRAFANA_PORT = 3000;
  static final int OTEL_COLLECTOR_PORT = 4318;
  private LocalPortForward prometheusPortForward;
  private LocalPortForward grafanaPortForward;
  private LocalPortForward otelCollectorPortForward;

  static final KubernetesClient client = new KubernetesClientBuilder().build();

  MetricsHandlingE2E() throws FileNotFoundException {}

  @RegisterExtension
  AbstractOperatorExtension operator =
      isLocal()
          ? LocallyRunOperatorExtension.builder()
              .withReconciler(new MetricsHandlingReconciler1())
              .withReconciler(new MetricsHandlingReconciler2())
              .withConfigurationService(
                  c -> c.withMetrics(MetricsHandlingSampleOperator.initOTLPMetrics(true)))
              .build()
          : ClusterDeployedOperatorExtension.builder()
              .withOperatorDeployment(client.load(new FileInputStream("k8s/operator.yaml")).items())
              .build();

  @BeforeAll
  void setupObservability() throws InterruptedException {
    log.info("Setting up observability stack...");
    installObservabilityServices();
    // Setup port forwarding to Prometheus
    log.info("Setting up port forwarding for Prometheus");
    setupPrometheusPortForward();
    if (isLocal()) {
      log.info("Setting up port forwarding for Otel collector and grafana");
      setupPortForwardForOtelCollector();
      setupPortForwardForGrafana();
    }
    Thread.sleep(2000);
  }

  @AfterAll
  void cleanup() {
    closePortForward(prometheusPortForward, "Prometheus");
    closePortForward(grafanaPortForward, "Grafana");
    closePortForward(otelCollectorPortForward, "OTel Collector");
  }

  private void closePortForward(LocalPortForward portForward, String name) {
    if (portForward != null) {
      try {
        portForward.close();
        log.info("Closed {} port forward", name);
      } catch (IOException e) {
        log.warn("Failed to close {} port forward", name, e);
      }
    }
  }

  // todo check historgram execution time
  // failoures by controller
  // delete event rate - delete resources in test
  // error rate
  @Test
  void testPropagatedMetrics() throws Exception {
    log.info("Starting longevity metrics test (running for ~50 seconds)");

    // Create initial resources including ones that trigger failures
    operator.create(createResource1("test-success-1", 42));
    operator.create(createResource2("test-success-2", 77));
    operator.create(createResource1("test-fail-1", 100));
    operator.create(createResource2("test-error-2", 200));

    // Continuously trigger reconciliations for ~50 seconds by alternating between
    // creating new resources, updating specs of existing ones, and deleting older dynamic ones
    long deadline = System.currentTimeMillis() + Duration.ofSeconds(50).toMillis();
    int counter = 0;
    Deque<String> createdResource1Names = new ArrayDeque<>();
    Deque<String> createdResource2Names = new ArrayDeque<>();
    while (System.currentTimeMillis() < deadline) {
      counter++;
      switch (counter % 4) {
        case 0 -> {
          String name = "test-dynamic-1-" + counter;
          operator.create(createResource1(name, counter * 3));
          createdResource1Names.addLast(name);
          log.info("Iteration {}: created {}", counter, name);
        }
        case 1 -> {
          var r1 = operator.get(MetricsHandlingCustomResource1.class, "test-success-1");
          r1.getSpec().setNumber(counter * 7);
          operator.replace(r1);
          log.info("Iteration {}: updated test-success-1 number to {}", counter, counter * 7);
        }
        case 2 -> {
          String name = "test-dynamic-2-" + counter;
          operator.create(createResource2(name, counter * 5));
          createdResource2Names.addLast(name);
          log.info("Iteration {}: created {}", counter, name);
        }
        case 3 -> {
          // Delete the oldest dynamic resource; prefer whichever type has more accumulated
          if (!createdResource1Names.isEmpty()
              && (createdResource2Names.isEmpty()
                  || createdResource1Names.size() >= createdResource2Names.size())) {
            String name = createdResource1Names.pollFirst();
            var r = operator.get(MetricsHandlingCustomResource1.class, name);
            if (r != null) {
              operator.delete(r);
              log.info("Iteration {}: deleted {}", counter, name);
            }
          } else if (!createdResource2Names.isEmpty()) {
            String name = createdResource2Names.pollFirst();
            var r = operator.get(MetricsHandlingCustomResource2.class, name);
            if (r != null) {
              operator.delete(r);
              log.info("Iteration {}: deleted {}", counter, name);
            }
          }
        }
      }
      Thread.sleep(1000);
    }

    log.info("Longevity phase completed ({} iterations), verifying metrics", counter);
    verifyPrometheusMetrics();
  }

  private void verifyPrometheusMetrics() throws Exception {
    log.info("Verifying metrics in Prometheus...");

    int localPort = prometheusPortForward.getLocalPort();
    String prometheusUrl = "http://localhost:" + localPort;

    // Verify reconciliation started metrics
    String startedQuery = "reconciliations_started_total";
    await()
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              String result = queryPrometheus(prometheusUrl, startedQuery);
              assertThat(result).contains("\"status\":\"success\"");
              assertThat(result).contains("reconciliations_started_total");
            });

    // Verify success metrics
    String successQuery = "reconciliations_success_total";
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              String result = queryPrometheus(prometheusUrl, successQuery);
              log.info("Reconciliations success metric: {}", result);
              assertThat(result).contains("\"status\":\"success\"");
              assertThat(result).contains("reconciliations_success_total");
            });

    // Verify failure metrics
    String failureQuery = "reconciliations_failure_total";
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              String result = queryPrometheus(prometheusUrl, failureQuery);
              log.info("Reconciliations failure metric: {}", result);
              assertThat(result).contains("\"status\":\"success\"");
              assertThat(result).contains("reconciliations_failure_total");
            });

    // Verify controller execution metrics
    String controllerQuery = "controllers_success_total";
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              String result = queryPrometheus(prometheusUrl, controllerQuery);
              log.info("Controller success metric: {}", result);
              assertThat(result).contains("\"status\":\"success\"");
            });

    // Verify execution time metrics
    String executionTimeQuery = "reconciliations_execution_seconds_count";
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
    spec.setNumber(number);
    resource.setSpec(spec);

    return resource;
  }

  private MetricsHandlingCustomResource2 createResource2(String name, int number) {
    MetricsHandlingCustomResource2 resource = new MetricsHandlingCustomResource2();
    resource.getMetadata().setName(name);

    MetricsHandlingSpec spec = new MetricsHandlingSpec();
    spec.setNumber(number);
    resource.setSpec(spec);

    return resource;
  }

  private void installObservabilityServices() {
    try {
      // Find the observability script relative to project root
      File projectRoot = new File(".").getCanonicalFile();
      while (projectRoot != null && !new File(projectRoot, "observability").exists()) {
        projectRoot = projectRoot.getParentFile();
      }

      if (projectRoot == null) {
        throw new IllegalStateException("Could not find observability directory");
      }

      File scriptFile = new File(projectRoot, "observability/install-observability.sh");
      if (!scriptFile.exists()) {
        throw new IllegalStateException(
            "Observability script not found at: " + scriptFile.getAbsolutePath());
      }

      log.info("Running observability setup script: {}", scriptFile.getAbsolutePath());

      // Run the install-observability.sh script
      ProcessBuilder processBuilder = new ProcessBuilder("/bin/sh", scriptFile.getAbsolutePath());
      processBuilder.redirectErrorStream(true);

      processBuilder.environment().putAll(System.getenv());
      Process process = processBuilder.start();
      BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      String line;
      while ((line = reader.readLine()) != null) {
        log.info("Observability setup: {}", line);
      }

      int exitCode = process.waitFor();
      if (exitCode != 0) {
        log.warn("Observability setup script returned exit code: {}", exitCode);
      }

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
    } catch (Exception e) {
      log.error("Failed to setup observability stack", e);
      throw new RuntimeException(e);
    }
  }

  private void setupPortForwardForGrafana() {
    grafanaPortForward = setupPortForward("grafana", GRAFANA_PORT);
  }

  private void setupPortForwardForOtelCollector() {
    otelCollectorPortForward = setupPortForward("otel-collector-collector", OTEL_COLLECTOR_PORT);
  }

  private void setupPrometheusPortForward() {
    prometheusPortForward = setupPortForward("prometheus", PROMETHEUS_PORT);
  }

  private LocalPortForward setupPortForward(String appName, int port) {
    try {
      Pod pod =
          operator
              .getKubernetesClient()
              .pods()
              .inNamespace(OBSERVABILITY_NAMESPACE)
              .withLabel("app.kubernetes.io/name", appName)
              .list()
              .getItems()
              .stream()
              .findFirst()
              .orElseThrow(() -> new IllegalStateException(appName + " pod not found"));

      log.info("Setting up port forward to {} pod: {}", appName, pod.getMetadata().getName());
      var portForward =
          operator
              .getKubernetesClient()
              .pods()
              .inNamespace(OBSERVABILITY_NAMESPACE)
              .withName(pod.getMetadata().getName())
              .portForward(port, port);

      log.info(
          "{} port forward established on local port: {}", appName, portForward.getLocalPort());
      return portForward;
    } catch (Exception e) {
      log.error("Failed to setup {} port forward", appName, e);
      throw new RuntimeException(e);
    }
  }

  AbstractOperatorExtension operator() {
    return operator;
  }
}
