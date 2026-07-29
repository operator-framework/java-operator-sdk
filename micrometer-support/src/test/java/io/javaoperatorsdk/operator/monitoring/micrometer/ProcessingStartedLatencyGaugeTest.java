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
package io.javaoperatorsdk.operator.monitoring.micrometer;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.Controller;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessingStartedLatencyGaugeTest {

  private static final String CONTROLLER_NAME = "testcontroller";

  @Test
  void latencyGaugeKeepsItsValue() {
    var registry = new SimpleMeterRegistry();
    var metrics = MicrometerMetricsV2.newBuilder(registry).build();

    metrics.eventProcessingStarted(controller());

    var gauge = registry.find(MicrometerMetricsV2.PROCESSING_STARTED_LATENCY_GAUGE).gauge();
    assertThat(gauge).isNotNull();
    assertThat(gauge.value()).isNotNaN().isPositive();

    // the registry holds only a weak reference to the gauged object
    forceGarbageCollection();

    assertThat(gauge.value()).isNotNaN().isPositive();
  }

  @Test
  void repeatedCallsUpdateTheSameGauge() {
    var registry = new SimpleMeterRegistry();
    var metrics = MicrometerMetricsV2.newBuilder(registry).build();

    metrics.eventProcessingStarted(controller());
    metrics.eventProcessingStarted(controller());

    assertThat(registry.find(MicrometerMetricsV2.PROCESSING_STARTED_LATENCY_GAUGE).gauges())
        .hasSize(1);
  }

  @SuppressWarnings("unchecked")
  private static Controller<ConfigMap> controller() {
    Controller<ConfigMap> controller = mock(Controller.class);
    ControllerConfiguration<ConfigMap> configuration = mock(ControllerConfiguration.class);
    when(controller.getConfiguration()).thenReturn(configuration);
    when(configuration.getName()).thenReturn(CONTROLLER_NAME);
    return controller;
  }

  private static void forceGarbageCollection() {
    for (int i = 0; i < 5; i++) {
      System.gc();
    }
  }
}
