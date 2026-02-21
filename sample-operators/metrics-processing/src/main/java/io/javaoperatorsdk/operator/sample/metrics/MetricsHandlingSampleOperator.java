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
import java.io.InputStream;
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
import io.javaoperatorsdk.operator.monitoring.micrometer.MicrometerMetricsV2;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.logging.LoggingMeterRegistry;
import io.micrometer.core.instrument.logging.LoggingRegistryConfig;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;

public class MetricsHandlingSampleOperator {

  private static final Logger log = LoggerFactory.getLogger(MetricsHandlingSampleOperator.class);
  public static final String OPERATOR_SDK_METRICS_PREFIX = "operator.sdk";

  /**
   * Based on env variables a different flavor of Reconciler is used, showcasing how the same logic
   * can be implemented using the low level and higher level APIs.
   */
  public static void main(String[] args) {
    log.info("Metrics Handling Sample Operator starting!");

    // Load configuration from config.yaml
    Metrics metrics = initOTLPMetrics(false);
    Operator operator =
        new Operator(o -> o.withStopOnInformerErrorDuringStartup(false).withMetrics(metrics));

    operator.register(new MetricsHandlingReconciler1());
    operator.register(new MetricsHandlingReconciler2());

    operator.start();
  }

  public static @NonNull Metrics initOTLPMetrics(boolean localRun) {
    CompositeMeterRegistry compositeRegistry = new CompositeMeterRegistry();

    // Add OTLP registry
    Map<String, String> configProperties = loadConfigFromYaml();
    if (localRun) {
      configProperties.put("otlp.url", "http://localhost:4318/v1/metrics");
    }
    var otlpConfig =
        new OtlpConfig() {
          @Override
          public String prefix() {
            return OPERATOR_SDK_METRICS_PREFIX;
          }

          @Override
          public @Nullable String get(String key) {
            return configProperties.get(key);
          }

          // these should come from env variables
          @Override
          public Map<String, String> resourceAttributes() {
            return Map.of("service.name", "josdk", "operator", "metrics-processing");
          }
        };

    MeterRegistry otlpRegistry = new OtlpMeterRegistry(otlpConfig, Clock.SYSTEM);
    compositeRegistry.add(otlpRegistry);

    String enableConsoleLogging = System.getenv("METRICS_CONSOLE_LOGGING");
    if ("true".equalsIgnoreCase(enableConsoleLogging)) {
      log.info("Console metrics logging enabled");
      LoggingMeterRegistry loggingRegistry =
          new LoggingMeterRegistry(
              new LoggingRegistryConfig() {
                @Override
                public String prefix() {
                  return OPERATOR_SDK_METRICS_PREFIX;
                }

                @Override
                public String get(String key) {
                  return null;
                }

                @Override
                public Duration step() {
                  return Duration.ofSeconds(15);
                }
              },
              Clock.SYSTEM);
      compositeRegistry.add(loggingRegistry);
    }

    // Register JVM and system metrics
    log.info("Registering JVM and system metrics...");

    new JvmMemoryMetrics().bindTo(compositeRegistry);
    new JvmGcMetrics().bindTo(compositeRegistry);
    new JvmThreadMetrics().bindTo(compositeRegistry);
    new ClassLoaderMetrics().bindTo(compositeRegistry);
    new ProcessorMetrics().bindTo(compositeRegistry);
    new UptimeMetrics().bindTo(compositeRegistry);

    return MicrometerMetricsV2.newPerResourceCollectingMicrometerMetricsBuilder(compositeRegistry)
        .build();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> loadConfigFromYaml() {
    Map<String, String> configMap = new HashMap<>();
    try (InputStream inputStream =
        MetricsHandlingSampleOperator.class.getResourceAsStream("/otlp-config.yaml")) {
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
