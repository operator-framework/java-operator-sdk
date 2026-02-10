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
package io.javaoperatorsdk.operator.sample;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.monitoring.micrometer.MicrometerMetrics;
import io.javaoperatorsdk.operator.sample.probes.LivenessHandler;
import io.javaoperatorsdk.operator.sample.probes.StartupHandler;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.logging.LoggingMeterRegistry;
import io.micrometer.core.instrument.logging.LoggingRegistryConfig;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;

import com.sun.net.httpserver.HttpServer;

public class WebPageOperator {
  public static final String WEBPAGE_RECONCILER_ENV = "WEBPAGE_RECONCILER";
  public static final String WEBPAGE_CLASSIC_RECONCILER_ENV_VALUE = "classic";
  public static final String WEBPAGE_MANAGED_DEPENDENT_RESOURCE_ENV_VALUE = "managed";
  private static final Logger log = LoggerFactory.getLogger(WebPageOperator.class);

  /**
   * Based on env variables a different flavor of Reconciler is used, showcasing how the same logic
   * can be implemented using the low level and higher level APIs.
   */
  public static void main(String[] args) throws IOException {
    log.info("WebServer Operator starting!");

    // TODO // todo change:
    //  operator_sdk_reconciliations_queue_size_webpagestandalonedependentsreconciler
    //  operator_sdk_reconciliations_executions_webpagestandalonedependentsreconciler
    //  => controller name as label
    // TODO add test for checking if there are metrics in prometheus
    // Load configuration from config.yaml
    Metrics metrics = initOTLPMetrics();
    Operator operator =
        new Operator(o -> o.withStopOnInformerErrorDuringStartup(false).withMetrics(metrics));

    String reconcilerEnvVar = System.getenv(WEBPAGE_RECONCILER_ENV);
    if (WEBPAGE_CLASSIC_RECONCILER_ENV_VALUE.equals(reconcilerEnvVar)) {
      operator.register(new WebPageReconciler());
    } else if (WEBPAGE_MANAGED_DEPENDENT_RESOURCE_ENV_VALUE.equals(reconcilerEnvVar)) {
      operator.register(new WebPageManagedDependentsReconciler());
    } else {
      operator.register(new WebPageStandaloneDependentsReconciler());
    }
    operator.start();

    HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
    server.createContext("/startup", new StartupHandler(operator));
    // we want to restart the operator if something goes wrong with (maybe just some) event sources
    server.createContext("/healthz", new LivenessHandler(operator));
    server.setExecutor(null);
    server.start();
  }

  private static @NonNull Metrics initOTLPMetrics() {
    CompositeMeterRegistry compositeRegistry = new CompositeMeterRegistry();

    // Add OTLP registry
    Map<String, String> configProperties = loadConfigFromYaml();
    var otlpConfig =
        new OtlpConfig() {
          @Override
          public String prefix() {
            return "";
          }

          @Override
          public @Nullable String get(String key) {
            return configProperties.get(key);
          }

          // these should come from env variables
          @Override
          public Map<String, String> resourceAttributes() {
            return Map.of("service.name", "josdk", "operator", "webpage");
          }
        };

    MeterRegistry otlpRegistry = new OtlpMeterRegistry(otlpConfig, Clock.SYSTEM);
    compositeRegistry.add(otlpRegistry);

    // Add console logging registry if enabled (for development)
    //    String enableConsoleLogging = System.getenv("METRICS_CONSOLE_LOGGING");
    // todo remove
    String enableConsoleLogging = "true";
    if ("true".equalsIgnoreCase(enableConsoleLogging)) {
      log.info("Console metrics logging enabled");
      LoggingMeterRegistry loggingRegistry =
          new LoggingMeterRegistry(
              new LoggingRegistryConfig() {
                @Override
                public String get(String key) {
                  return null;
                }

                @Override
                public Duration step() {
                  return Duration.ofSeconds(10); // Log metrics every 30 seconds
                }
              },
              Clock.SYSTEM);
      compositeRegistry.add(loggingRegistry);
    }

    // Register JVM and system metrics
    log.info("Registering JVM and system metrics...");
    // todo add back
    //    new JvmMemoryMetrics().bindTo(compositeRegistry);
    //    new JvmGcMetrics().bindTo(compositeRegistry);
    //    new JvmThreadMetrics().bindTo(compositeRegistry);
    //    new ClassLoaderMetrics().bindTo(compositeRegistry);
    //    new ProcessorMetrics().bindTo(compositeRegistry);
    //    new UptimeMetrics().bindTo(compositeRegistry);

    return MicrometerMetrics.newPerResourceCollectingMicrometerMetricsBuilder(compositeRegistry)
        .collectingMetricsPerResource()
        .build();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> loadConfigFromYaml() {
    Map<String, String> configMap = new HashMap<>();
    try (InputStream inputStream = WebPageOperator.class.getResourceAsStream("/otlp-config.yaml")) {
      if (inputStream == null) {
        log.warn("otlp-config.yaml not found in resources, using default OTLP configuration");
        return configMap;
      }

      Yaml yaml = new Yaml();
      Map<String, Object> yamlData = yaml.load(inputStream);

      // Navigate to otlp section and map properties directly
      Map<String, Object> otlp = (Map<String, Object>) yamlData.get("otlp");
      if (otlp != null) {
        otlp.forEach((key, value) -> configMap.put("otlp." + key, value.toString()));
      }

      log.info("Loaded OTLP configuration from otlp-config.yaml: {}", configMap);
    } catch (IOException e) {
      log.error("Error loading otlp-config.yaml", e);
    }
    return configMap;
  }
}
