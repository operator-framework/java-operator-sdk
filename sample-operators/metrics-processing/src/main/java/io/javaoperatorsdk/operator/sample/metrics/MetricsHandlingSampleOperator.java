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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.monitoring.micrometer.MicrometerMetricsV2;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.logging.LoggingMeterRegistry;
import io.micrometer.core.instrument.logging.LoggingRegistryConfig;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import com.sun.net.httpserver.HttpServer;

public class MetricsHandlingSampleOperator {

  private static final Logger log = LoggerFactory.getLogger(MetricsHandlingSampleOperator.class);
  static final int METRICS_PORT = 8080;

  public static boolean isLocal() {
    String deployment = System.getProperty("test.deployment");
    boolean remote = (deployment != null && deployment.equals("remote"));
    log.info("Running the operator {} ", remote ? "remotely" : "locally");
    return !remote;
  }

  public static void main(String[] args) throws IOException {
    log.info("Metrics Handling Sample Operator starting!");

    var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    startMetricsServer(registry);

    Metrics metrics = initMetrics(registry);
    Operator operator =
        new Operator(o -> o.withStopOnInformerErrorDuringStartup(false).withMetrics(metrics));
    operator.register(new MetricsHandlingReconciler1());
    operator.register(new MetricsHandlingReconciler2());
    operator.start();
  }

  public static @NonNull Metrics initMetrics(PrometheusMeterRegistry registry) {
    // enable to easily see propagated metrics
    String enableConsoleLogging = System.getenv("METRICS_CONSOLE_LOGGING");
    if ("true".equalsIgnoreCase(enableConsoleLogging)) {
      log.info("Console metrics logging enabled");
      var loggingRegistry =
          new LoggingMeterRegistry(
              new LoggingRegistryConfig() {
                @Override
                public String get(String key) {
                  return null;
                }

                @Override
                public Duration step() {
                  return Duration.ofSeconds(10);
                }
              },
              Clock.SYSTEM);
      var composite = new CompositeMeterRegistry();
      composite.add(registry);
      composite.add(loggingRegistry);

      log.info("Registering JVM and system metrics...");
      new JvmMemoryMetrics().bindTo(composite);
      new JvmGcMetrics().bindTo(composite);
      new JvmThreadMetrics().bindTo(composite);
      new ClassLoaderMetrics().bindTo(composite);
      new ProcessorMetrics().bindTo(composite);
      new UptimeMetrics().bindTo(composite);

      return MicrometerMetricsV2.newBuilder(composite).build();
    }

    // Register JVM and system metrics
    log.info("Registering JVM and system metrics...");
    new JvmMemoryMetrics().bindTo(registry);
    new JvmGcMetrics().bindTo(registry);
    new JvmThreadMetrics().bindTo(registry);
    new ClassLoaderMetrics().bindTo(registry);
    new ProcessorMetrics().bindTo(registry);
    new UptimeMetrics().bindTo(registry);

    return MicrometerMetricsV2.newBuilder(registry).build();
  }

  static void startMetricsServer(PrometheusMeterRegistry registry) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(METRICS_PORT), 0);
    server.createContext(
        "/metrics",
        exchange -> {
          String response = registry.scrape();
          exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
          exchange.sendResponseHeaders(200, response.getBytes().length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
          }
        });
    server.start();
    log.info("Prometheus metrics endpoint started on port {}", METRICS_PORT);
  }
}
