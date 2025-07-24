package io.javaoperatorsdk.operator.baseapi.multiplesecondaryeventsource;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.awaitility.Awaitility.await;

class MultipleSecondaryEventSourceIT {

  public static final String TEST_RESOURCE_NAME = "testresource";

  @RegisterExtension
  static LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(MultipleSecondaryEventSourceReconciler.class)
          .build();

  @Test
  void receivingPeriodicEvents() {
    MultipleSecondaryEventSourceCustomResource resource = createTestCustomResource();

    operator.create(resource);

    var reconciler = operator.getReconcilerOfType(MultipleSecondaryEventSourceReconciler.class);

    await().pollDelay(Duration.ofMillis(300)).until(() -> reconciler.getNumberOfExecutions() <= 3);

    int numberOfInitialExecutions = reconciler.getNumberOfExecutions();

    updateConfigMap(resource, 1);

    await()
        .pollDelay(Duration.ofMillis(300))
        .until(() -> reconciler.getNumberOfExecutions() == numberOfInitialExecutions + 1);

    updateConfigMap(resource, 2);

    await()
        .pollDelay(Duration.ofMillis(300))
        .until(() -> reconciler.getNumberOfExecutions() == numberOfInitialExecutions + 2);
  }

  private void updateConfigMap(MultipleSecondaryEventSourceCustomResource resource, int number) {
    ConfigMap map1 =
        operator.get(
            ConfigMap.class,
            number == 1
                ? MultipleSecondaryEventSourceReconciler.getName1(resource)
                : MultipleSecondaryEventSourceReconciler.getName2(resource));
    map1.getData().put("value2", "value2");
    operator.replace(map1);
  }

  public MultipleSecondaryEventSourceCustomResource createTestCustomResource() {
    MultipleSecondaryEventSourceCustomResource resource =
        new MultipleSecondaryEventSourceCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName(TEST_RESOURCE_NAME)
            .withNamespace(operator.getNamespace())
            .build());
    return resource;
  }
}
