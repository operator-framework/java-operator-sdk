package io.javaoperatorsdk.operator.baseapi.dynamicgenericeventsourceregistration;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class DynamicGenericEventSourceRegistrationIT {

  public static final String TEST_RESOURCE_NAME = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(DynamicGenericEventSourceRegistrationReconciler.class)
          .build();

  @Test
  void registersEventSourcesDynamically() {
    var reconciler =
        extension.getReconcilerOfType(DynamicGenericEventSourceRegistrationReconciler.class);
    extension.create(testResource());

    await()
        .pollDelay(Duration.ofMillis(150))
        .untilAsserted(
            () -> {
              var cm = extension.get(ConfigMap.class, TEST_RESOURCE_NAME);
              var secret = extension.get(Secret.class, TEST_RESOURCE_NAME);
              assertThat(cm).isNotNull();
              assertThat(secret).isNotNull();
            });
    var executions = reconciler.getNumberOfExecutions();
    assertThat(reconciler.getNumberOfEventSources()).isEqualTo(2);
    assertThat(executions).isLessThanOrEqualTo(3);

    var cm = extension.get(ConfigMap.class, TEST_RESOURCE_NAME);
    cm.getData().put("key2", "val2");

    extension.replace(cm); // triggers the reconciliation

    await()
        .untilAsserted(
            () -> {
              assertThat(reconciler.getNumberOfExecutions() - executions).isEqualTo(2);
            });
    assertThat(reconciler.getNumberOfEventSources()).isEqualTo(2);
  }

  DynamicGenericEventSourceRegistrationCustomResource testResource() {
    var res = new DynamicGenericEventSourceRegistrationCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    return res;
  }
}
