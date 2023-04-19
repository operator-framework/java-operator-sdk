package io.javaoperatorsdk.operator;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.jenvtest.junit.EnableKubeAPIServer;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.event.EventSourceTestCustomReconciler;
import io.javaoperatorsdk.operator.sample.event.EventSourceTestCustomResource;
import io.javaoperatorsdk.operator.sample.event.EventSourceTestCustomResourceSpec;
import io.javaoperatorsdk.operator.support.TestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@EnableKubeAPIServer
class EventSourceIT {

  static KubernetesClient client;

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withKubernetesClient(client)
          .waitForNamespaceDeletion(false)
          .withReconciler(EventSourceTestCustomReconciler.class)
          .build();

  @Test
  void receivingPeriodicEvents() {
    EventSourceTestCustomResource resource = createTestCustomResource("1");

    operator.create(resource);

    await()
        .atMost(5, TimeUnit.SECONDS)
        .pollInterval(
            EventSourceTestCustomReconciler.TIMER_PERIOD / 2, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> assertThat(TestUtils.getNumberOfExecutions(operator))
                .isGreaterThanOrEqualTo(4));
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
