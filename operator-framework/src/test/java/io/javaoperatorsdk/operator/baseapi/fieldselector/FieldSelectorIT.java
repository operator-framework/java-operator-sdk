package io.javaoperatorsdk.operator.baseapi.fieldselector;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.fieldselector.FieldSelectorTestReconciler.MY_SECRET_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class FieldSelectorIT {

  public static final String TEST_1 = "test1";
  public static final String TEST_2 = "test2";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new FieldSelectorTestReconciler())
          .build();

  @Test
  void filtersCustomResourceByLabel() {

    extension.create(
        new SecretBuilder()
            .withMetadata(new ObjectMetaBuilder().withName(TEST_1).build())
            .withStringData(Map.of("key1", "value1"))
            .withType(MY_SECRET_TYPE)
            .build());

    extension.create(
        new SecretBuilder()
            .withMetadata(new ObjectMetaBuilder().withName(TEST_2).build())
            .withStringData(Map.of("key2", "value2"))
            .build());

    await()
        .pollDelay(Duration.ofMillis(150))
        .untilAsserted(
            () -> {
              var r = extension.getReconcilerOfType(FieldSelectorTestReconciler.class);
              assertThat(r.getReconciledSecrets()).containsExactly(TEST_1);
            });
  }
}
