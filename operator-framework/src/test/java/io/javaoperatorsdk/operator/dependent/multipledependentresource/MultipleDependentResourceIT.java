package io.javaoperatorsdk.operator.dependent.multipledependentresource;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.dependent.multipledependentresource.MultipleDependentResourceConfigMap.DATA_KEY;
import static io.javaoperatorsdk.operator.dependent.multipledependentresource.MultipleDependentResourceConfigMap.getConfigMapName;
import static io.javaoperatorsdk.operator.dependent.multipledependentresource.MultipleDependentResourceReconciler.FIRST_CONFIG_MAP_ID;
import static io.javaoperatorsdk.operator.dependent.multipledependentresource.MultipleDependentResourceReconciler.SECOND_CONFIG_MAP_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class MultipleDependentResourceIT {

  public static final String CHANGED_VALUE = "changed value";
  public static final String INITIAL_VALUE = "initial value";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new MultipleDependentResourceReconciler())
          .build();

  @Test
  void handlesCRUDOperations() {
    var res = extension.create(testResource());

    await()
        .untilAsserted(
            () -> {
              var cm1 = extension.get(ConfigMap.class, getConfigMapName(FIRST_CONFIG_MAP_ID));
              var cm2 = extension.get(ConfigMap.class, getConfigMapName(SECOND_CONFIG_MAP_ID));

              assertThat(cm1).isNotNull();
              assertThat(cm2).isNotNull();
              assertThat(cm1.getData()).containsEntry(DATA_KEY, INITIAL_VALUE);
              assertThat(cm2.getData()).containsEntry(DATA_KEY, INITIAL_VALUE);
            });

    res.getSpec().setValue(CHANGED_VALUE);
    res = extension.replace(res);

    await()
        .untilAsserted(
            () -> {
              var cm1 = extension.get(ConfigMap.class, getConfigMapName(FIRST_CONFIG_MAP_ID));
              var cm2 = extension.get(ConfigMap.class, getConfigMapName(SECOND_CONFIG_MAP_ID));

              assertThat(cm1.getData()).containsEntry(DATA_KEY, CHANGED_VALUE);
              assertThat(cm2.getData()).containsEntry(DATA_KEY, CHANGED_VALUE);
            });

    extension.delete(res);

    await()
        .timeout(Duration.ofSeconds(120))
        .untilAsserted(
            () -> {
              var cm1 = extension.get(ConfigMap.class, getConfigMapName(FIRST_CONFIG_MAP_ID));
              var cm2 = extension.get(ConfigMap.class, getConfigMapName(SECOND_CONFIG_MAP_ID));

              assertThat(cm1).isNull();
              assertThat(cm2).isNull();
            });
  }

  MultipleDependentResourceCustomResource testResource() {
    var res = new MultipleDependentResourceCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName("test1").build());
    res.setSpec(new MultipleDependentResourceSpec());
    res.getSpec().setValue(INITIAL_VALUE);

    return res;
  }
}
