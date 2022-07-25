package io.javaoperatorsdk.operator;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.createupdateeventfilter.CreateUpdateEventFilterTestCustomResource;
import io.javaoperatorsdk.operator.sample.createupdateeventfilter.CreateUpdateEventFilterTestCustomResourceSpec;
import io.javaoperatorsdk.operator.sample.createupdateeventfilter.CreateUpdateEventFilterTestReconciler;

import static io.javaoperatorsdk.operator.sample.createupdateeventfilter.CreateUpdateEventFilterTestReconciler.CONFIG_MAP_TEST_DATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class CreateUpdateInformerEventSourceEventFilterIT {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new CreateUpdateEventFilterTestReconciler())
          .build();

  @Test
  void updateEventNotReceivedAfterCreateOrUpdate() {
    CreateUpdateEventFilterTestCustomResource resource = prepareTestResource();
    var createdResource =
        operator.create(resource);

    await()
        .atMost(Duration.ofSeconds(1))
        .until(() -> {
          var cm = operator.get(ConfigMap.class, createdResource.getMetadata().getName());
          if (cm == null) {
            return false;
          }
          return cm.getData()
              .get(CONFIG_MAP_TEST_DATA_KEY)
              .equals(createdResource.getSpec().getValue());
        });

    assertThat(
        ((CreateUpdateEventFilterTestReconciler) operator.getFirstReconciler())
            .getNumberOfExecutions())
        .isEqualTo(1); // this should be 1 usually but sometimes event is received
    // faster than added resource added to the cache


    CreateUpdateEventFilterTestCustomResource actualCreatedResource =
        operator.get(CreateUpdateEventFilterTestCustomResource.class,
            resource.getMetadata().getName());
    actualCreatedResource.getSpec().setValue("2");
    operator.replace(actualCreatedResource);


    await().atMost(Duration.ofSeconds(1))
        .until(() -> {
          var cm = operator.get(ConfigMap.class, createdResource.getMetadata().getName());
          if (cm == null) {
            return false;
          }
          return cm.getData()
              .get(CONFIG_MAP_TEST_DATA_KEY)
              .equals(actualCreatedResource.getSpec().getValue());
        });

    assertThat(
        ((CreateUpdateEventFilterTestReconciler) operator.getFirstReconciler())
            .getNumberOfExecutions())
        // same as for previous assert (usually this should be 2)
        .isEqualTo(2);
  }

  private CreateUpdateEventFilterTestCustomResource prepareTestResource() {
    CreateUpdateEventFilterTestCustomResource resource =
        new CreateUpdateEventFilterTestCustomResource();
    resource.setMetadata(new ObjectMeta());
    resource.getMetadata().setName("test1");
    resource.setSpec(new CreateUpdateEventFilterTestCustomResourceSpec());
    resource.getSpec().setValue("1");
    return resource;
  }
}
