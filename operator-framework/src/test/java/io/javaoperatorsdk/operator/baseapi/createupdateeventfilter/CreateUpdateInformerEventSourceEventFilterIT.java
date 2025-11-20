package io.javaoperatorsdk.operator.baseapi.createupdateeventfilter;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.createupdateeventfilter.CreateUpdateEventFilterTestReconciler.CONFIG_MAP_TEST_DATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Event filtering for create and update operations",
    description =
        "Shows how to configure event filters on informer event sources to control which create and"
            + " update events trigger reconciliation. This is useful for preventing unnecessary"
            + " reconciliation loops when dependent resources are modified by the controller"
            + " itself.")
class CreateUpdateInformerEventSourceEventFilterIT {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new CreateUpdateEventFilterTestReconciler())
          .build();

  @Test
  void updateEventNotReceivedAfterCreateOrUpdate() {
    CreateUpdateEventFilterTestCustomResource resource =
        CreateUpdateInformerEventSourceEventFilterIT.prepareTestResource();
    var createdResource = operator.create(resource);

    assertData(operator, createdResource, 1, 1);

    CreateUpdateEventFilterTestCustomResource actualCreatedResource =
        operator.get(
            CreateUpdateEventFilterTestCustomResource.class, resource.getMetadata().getName());
    actualCreatedResource.getSpec().setValue("2");
    operator.replace(actualCreatedResource);

    assertData(operator, actualCreatedResource, 2, 2);
  }

  static void assertData(
      LocallyRunOperatorExtension operator,
      CreateUpdateEventFilterTestCustomResource resource,
      int minExecutions,
      int maxExecutions) {
    await()
        .atMost(Duration.ofSeconds(1))
        .until(
            () -> {
              var cm = operator.get(ConfigMap.class, resource.getMetadata().getName());
              if (cm == null) {
                return false;
              }
              return cm.getData()
                  .get(CONFIG_MAP_TEST_DATA_KEY)
                  .equals(resource.getSpec().getValue());
            });

    int numberOfExecutions =
        ((CreateUpdateEventFilterTestReconciler) operator.getFirstReconciler())
            .getNumberOfExecutions();
    assertThat(numberOfExecutions).isGreaterThanOrEqualTo(minExecutions);
    assertThat(numberOfExecutions).isLessThanOrEqualTo(maxExecutions);
  }

  static CreateUpdateEventFilterTestCustomResource prepareTestResource() {
    CreateUpdateEventFilterTestCustomResource resource =
        new CreateUpdateEventFilterTestCustomResource();
    resource.setMetadata(new ObjectMeta());
    resource.getMetadata().setName("test1");
    resource.setSpec(new CreateUpdateEventFilterTestCustomResourceSpec());
    resource.getSpec().setValue("1");
    return resource;
  }
}
