package io.javaoperatorsdk.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;
import io.javaoperatorsdk.operator.junit.OperatorExtension;
import io.javaoperatorsdk.operator.sample.event.EventSourceTestCustomResource;
import io.javaoperatorsdk.operator.sample.event.EventSourceTestCustomResourceController;
import io.javaoperatorsdk.operator.sample.event.EventSourceTestCustomResourceSpec;
import io.javaoperatorsdk.operator.support.TestUtils;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class EventSourceIT {
  @RegisterExtension
  OperatorExtension operator =
      OperatorExtension.builder()
          .withConfigurationService(DefaultConfigurationService.instance())
          .withController(EventSourceTestCustomResourceController.class)
          .build();

  @Test
  public void receivingPeriodicEvents() {
    EventSourceTestCustomResource resource = createTestCustomResource("1");

    operator.create(EventSourceTestCustomResource.class, resource);

    await()
        .atMost(5, TimeUnit.SECONDS)
        .pollInterval(
            EventSourceTestCustomResourceController.TIMER_PERIOD / 2, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> {
              assertThat(TestUtils.getNumberOfExecutions(operator))
                  .isGreaterThanOrEqualTo(4);
            });
  }

  public EventSourceTestCustomResource createTestCustomResource(String id) {
    EventSourceTestCustomResource resource = new EventSourceTestCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName("eventsource-" + id)
            .withNamespace(operator.getNamespace())
            .withFinalizers(EventSourceTestCustomResourceController.FINALIZER_NAME)
            .build());
    resource.setKind("Eventsourcesample");
    resource.setSpec(new EventSourceTestCustomResourceSpec());
    resource.getSpec().setValue(id);
    return resource;
  }
}
