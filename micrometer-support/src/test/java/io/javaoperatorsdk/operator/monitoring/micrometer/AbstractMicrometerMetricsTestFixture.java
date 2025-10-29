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

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractMicrometerMetricsTestFixture {

  protected final TestSimpleMeterRegistry registry = new TestSimpleMeterRegistry();
  protected final MicrometerMetrics metrics = getMetrics();
  protected static final String testResourceName = "micrometer-metrics-cr";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withConfigurationService(overrider -> overrider.withMetrics(metrics))
          .withReconciler(new MetricsCleaningTestReconciler())
          .build();

  protected abstract MicrometerMetrics getMetrics();

  @Test
  void properlyHandlesResourceDeletion() throws Exception {
    var testResource =
        new ConfigMapBuilder().withNewMetadata().withName(testResourceName).endMetadata().build();
    final var created = operator.create(testResource);

    // make sure the resource is created
    await()
        .until(
            () ->
                !operator
                    .get(ConfigMap.class, testResourceName)
                    .getMetadata()
                    .getFinalizers()
                    .isEmpty());

    final var resourceID = ResourceID.fromResource(created);
    final var meters = preDeleteChecks(resourceID);

    // delete the resource and wait for it to be deleted
    operator.delete(testResource);
    await().until(() -> operator.get(ConfigMap.class, testResourceName) == null);

    postDeleteChecks(resourceID, meters);
  }

  protected Set<Meter.Id> preDeleteChecks(ResourceID resourceID) {
    // check that we properly recorded meters associated with the resource
    final var meters = metrics.recordedMeterIdsFor(resourceID);
    // metrics are collected per resource
    assertThat(registry.getMetersAsString()).contains(resourceID.getName());
    assertThat(meters).isNotNull();
    assertThat(meters).isNotEmpty();
    return meters;
  }

  protected void postDeleteChecks(ResourceID resourceID, Set<Meter.Id> recordedMeters)
      throws Exception {}

  @ControllerConfiguration
  private static class MetricsCleaningTestReconciler
      implements Reconciler<ConfigMap>, Cleaner<ConfigMap> {
    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return UpdateControl.noUpdate();
    }

    @Override
    public DeleteControl cleanup(ConfigMap resource, Context<ConfigMap> context) {
      return DeleteControl.defaultDelete();
    }
  }

  static class TestSimpleMeterRegistry extends SimpleMeterRegistry {
    private final Set<Meter.Id> removed = new HashSet<>();

    @Override
    public Meter remove(Meter.Id mappedId) {
      final var removed = super.remove(mappedId);
      this.removed.add(removed.getId());
      return removed;
    }

    public Set<Meter.Id> getRemoved() {
      return removed;
    }
  }
}
