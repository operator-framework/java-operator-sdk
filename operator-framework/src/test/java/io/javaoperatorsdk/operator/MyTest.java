package io.javaoperatorsdk.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;
import io.javaoperatorsdk.operator.junit.OperatorExtension;
import io.javaoperatorsdk.operator.sample.event.EventSourceTestCustomResource;
import io.javaoperatorsdk.operator.sample.event.EventSourceTestCustomResourceController;
import io.javaoperatorsdk.operator.sample.event.EventSourceTestCustomResourceSpec;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class MyTest {

  @RegisterExtension
  OperatorExtension operator =
      OperatorExtension.builder()
          .withConfigurationService(DefaultConfigurationService.instance())
          .withController(EventSourceTestCustomResourceController.class)
          .preserveNamespaceOnError(false)
          .build();

  @Test
  public void receivingPeriodicEvents() {
    EventSourceTestCustomResource resource = createTestCustomResource("1");

    operator.getResourceClient(EventSourceTestCustomResource.class).create(resource);

    await()
        .atMost(5, TimeUnit.SECONDS)
        .pollDelay(EventSourceTestCustomResourceController.TIMER_DELAY, TimeUnit.MILLISECONDS)
        .pollInterval(EventSourceTestCustomResourceController.TIMER_PERIOD, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> {
              for (var controller : operator.controllers()) {
                if (controller instanceof TestExecutionInfoProvider) {
                  assertThat(((TestExecutionInfoProvider) controller).getNumberOfExecutions())
                      .isGreaterThanOrEqualTo(4);
                }
              }
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
