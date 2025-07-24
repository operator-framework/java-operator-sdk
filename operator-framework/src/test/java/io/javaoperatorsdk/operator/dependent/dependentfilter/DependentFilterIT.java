package io.javaoperatorsdk.operator.dependent.dependentfilter;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.dependent.dependentfilter.DependentFilterTestReconciler.CM_VALUE_KEY;
import static io.javaoperatorsdk.operator.dependent.dependentfilter.DependentFilterTestReconciler.CONFIG_MAP_FILTER_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class DependentFilterIT {

  public static final String RESOURCE_NAME = "test1";

  @RegisterExtension
  static LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(DependentFilterTestReconciler.class)
          .build();

  @Test
  void filtersUpdateOnConfigMap() {
    var resource = createResource();
    operator.create(resource);

    await()
        .pollDelay(Duration.ofMillis(150))
        .untilAsserted(
            () -> {
              assertThat(
                      operator
                          .getReconcilerOfType(DependentFilterTestReconciler.class)
                          .getNumberOfExecutions())
                  .isEqualTo(1);
            });

    var configMap = operator.get(ConfigMap.class, RESOURCE_NAME);
    configMap.setData(Map.of(CM_VALUE_KEY, CONFIG_MAP_FILTER_VALUE));
    operator.replace(configMap);

    await()
        .pollDelay(Duration.ofMillis(150))
        .untilAsserted(
            () -> {
              assertThat(
                      operator
                          .getReconcilerOfType(DependentFilterTestReconciler.class)
                          .getNumberOfExecutions())
                  .isEqualTo(1);
            });
  }

  DependentFilterTestCustomResource createResource() {
    DependentFilterTestCustomResource resource = new DependentFilterTestCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME).build());
    resource.setSpec(new DependentFilterTestResourceSpec());
    resource.getSpec().setValue("value1");
    return resource;
  }
}
