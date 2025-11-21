package io.javaoperatorsdk.operator.baseapi.event;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.support.TestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Custom Event Source for Periodic Reconciliation",
    description =
        """
        Demonstrates how to implement custom event sources that trigger reconciliation on a \
        periodic basis. The test verifies that reconciliations are triggered at regular intervals \
        by a timer-based event source. This enables operators to perform periodic checks or \
        updates independent of resource changes.
        """)
class EventSourceIT {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(EventSourceTestCustomReconciler.class)
          .build();

  @Test
  void receivingPeriodicEvents() {
    EventSourceTestCustomResource resource = createTestCustomResource("1");

    operator.create(resource);

    await()
        .atMost(5, TimeUnit.SECONDS)
        .pollInterval(EventSourceTestCustomReconciler.TIMER_PERIOD / 2, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> assertThat(TestUtils.getNumberOfExecutions(operator)).isGreaterThanOrEqualTo(4));
  }

  public EventSourceTestCustomResource createTestCustomResource(String id) {
    EventSourceTestCustomResource resource = new EventSourceTestCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName("eventsource-" + id)
            .withNamespace(operator.getNamespace())
            .build());
    resource.setSpec(new EventSourceTestCustomResourceSpec());
    resource.getSpec().setValue(id);
    return resource;
  }
}
