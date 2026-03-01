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

import io.fabric8.kubernetes.client.CustomResource;
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
  public static final Duration TEST_DURATION = Duration.ofSeconds(60);
  public static final String NAME_LABEL_KEY = "app.kubernetes.io/name";

  private LocalPortForward prometheusPortForward;
  private LocalPortForward otelCollectorPortForward;
  private LocalPortForward grafanaPortForward;

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
              .withOperatorDeployment(
                  new KubernetesClientBuilder()
                      .build()
                      .load(new FileInputStream("k8s/operator.yaml"))
                      .items())
              .build();

  @BeforeAll
  void setupObservability() throws InterruptedException {
    log.info("Setting up observability stack...");
    installObservabilityServices();
    prometheusPortForward = portForward(NAME_LABEL_KEY, "prometheus", PROMETHEUS_PORT);
    if (isLocal()) {
      otelCollectorPortForward =
          portForward(NAME_LABEL_KEY, "otel-collector-collector", OTEL_COLLECTOR_PORT);
      grafanaPortForward = portForward(NAME_LABEL_KEY, "grafana", GRAFANA_PORT);
    }
    Thread.sleep(2000);
  }

  @AfterAll
  void cleanup() throws IOException {
    closePortForward(prometheusPortForward);
    closePortForward(otelCollectorPortForward);
    closePortForward(grafanaPortForward);
  }

  private LocalPortForward portForward(String labelKey, String labelValue, int port) {
    return client
        .pods()
        .inNamespace(OBSERVABILITY_NAMESPACE)
        .withLabel(labelKey, labelValue)
        .list()
        .getItems()
        .stream()
        .findFirst()
        .map(
            pod ->
                client
                    .pods()
                    .inNamespace(OBSERVABILITY_NAMESPACE)
                    .withName(pod.getMetadata().getName())
                    .portForward(port, port))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Pod not found for label " + labelKey + "=" + labelValue));
  }

  private void closePortForward(LocalPortForward pf) throws IOException {
    if (pf != null) {
      pf.close();
    }
  }

  // note that we just cover here cases that should be visible in metrics,
  // including errors, delete events.
  @Test
  void testPropagatedMetrics() throws Exception {
    log.info(
        "Starting longevity metrics test (running for {} seconds)", TEST_DURATION.getSeconds());

    // Create initial resources including ones that trigger failures
    operator.create(createResource(MetricsHandlingCustomResource1.class, "test-success-1", 1));
    operator.create(createResource(MetricsHandlingCustomResource2.class, "test-success-2", 1));
    operator.create(createResource(MetricsHandlingCustomResource1.class, "test-fail-1", 1));
    operator.create(createResource(MetricsHandlingCustomResource2.class, "test-fail-2", 1));

    // Continuously trigger reconciliations for ~50 seconds by alternating between
    // creating new resources, updating specs of existing ones, and deleting older dynamic ones
    long deadline = System.currentTimeMillis() + TEST_DURATION.toMillis();
    int counter = 0;
    Deque<String> createdResource1Names = new ArrayDeque<>();
    Deque<String> createdResource2Names = new ArrayDeque<>();
    while (System.currentTimeMillis() < deadline) {
      counter++;
      switch (counter % 4) {
        case 0 -> {
          String name = "test-dynamic-1-" + counter;
          operator.create(createResource(MetricsHandlingCustomResource1.class, name, counter * 3));
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
          operator.create(createResource(MetricsHandlingCustomResource2.class, name, counter * 5));
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
              log.info("Iteration {}: deleted {} ", counter, name);
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

  private void verifyPrometheusMetrics() {
    log.info("Verifying metrics in Prometheus...");
    String prometheusUrl = "http://localhost:" + PROMETHEUS_PORT;

    assertMetricPresent(prometheusUrl, "reconciliations_started_total", Duration.ofSeconds(60));
    assertMetricPresent(prometheusUrl, "reconciliations_success_total", Duration.ofSeconds(30));
    assertMetricPresent(prometheusUrl, "reconciliations_failure_total", Duration.ofSeconds(30));
    assertMetricPresent(
        prometheusUrl, "reconciliations_execution_duration_milliseconds_count", Duration.ofSeconds(30));

    log.info("All metrics verified successfully in Prometheus");
  }

  private void assertMetricPresent(String prometheusUrl, String metricName, Duration timeout) {
    await()
        .atMost(timeout)
        .pollInterval(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              String result = queryPrometheus(prometheusUrl, metricName);
              log.info("{}: {}", metricName, result);
              assertThat(result).contains("\"status\":\"success\"");
              assertThat(result).contains(metricName);
            });
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

  private <R extends CustomResource<MetricsHandlingSpec, ?>> R createResource(
      Class<R> type, String name, int number) {
    try {
      R resource = type.getDeclaredConstructor().newInstance();
      resource.getMetadata().setName(name);
      MetricsHandlingSpec spec = new MetricsHandlingSpec();
      spec.setNumber(number);
      resource.setSpec(spec);
      return resource;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
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
      log.info("Observability stack is ready");
    } catch (Exception e) {
      log.error("Failed to setup observability stack", e);
      throw new RuntimeException(e);
    }
  }
}
