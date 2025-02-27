package io.javaoperatorsdk.operator.dependent.createonlyifnotexistsdependentwithssa;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class CreateOnlyIfNotExistingDependentWithSSAIT {

  public static final String TEST_RESOURCE_NAME = "test1";
  public static final String KEY = "key";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new CreateOnlyIfNotExistingDependentWithSSAReconciler())
          .build();

  @Test
  void createsResourceOnlyIfNotExisting() {
    var cm =
        new ConfigMapBuilder()
            .withMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build())
            .withData(Map.of(KEY, "val"))
            .build();

    extension.create(cm);
    extension.create(testResource());

    await()
        .pollDelay(Duration.ofMillis(200))
        .untilAsserted(
            () -> {
              var currentCM = extension.get(ConfigMap.class, TEST_RESOURCE_NAME);
              assertThat(currentCM.getData()).containsKey(KEY);
            });
  }

  CreateOnlyIfNotExistingDependentWithSSACustomResource testResource() {
    var res = new CreateOnlyIfNotExistingDependentWithSSACustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());

    return res;
  }
}
