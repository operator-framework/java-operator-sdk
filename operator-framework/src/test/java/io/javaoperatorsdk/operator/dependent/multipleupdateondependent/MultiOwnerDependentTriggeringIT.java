package io.javaoperatorsdk.operator.dependent.multipleupdateondependent;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class MultiOwnerDependentTriggeringIT {

  public static final String VALUE_1 = "value1";
  public static final String VALUE_2 = "value2";
  public static final String NEW_VALUE_1 = "newValue1";
  public static final String NEW_VALUE_2 = "newValue2";

  @RegisterExtension
  static LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withConfigurationService(o -> o.withDefaultNonSSAResource(Set.of()))
          .withReconciler(MultipleOwnerDependentReconciler.class)
          .build();

  @Test
  void multiOwnerTriggeringAndManagement() {
    var res1 = extension.create(testResource("res1", VALUE_1));
    var res2 = extension.create(testResource("res2", VALUE_2));

    await()
        .untilAsserted(
            () -> {
              var cm =
                  extension.get(ConfigMap.class, MultipleOwnerDependentConfigMap.RESOURCE_NAME);

              assertThat(cm).isNotNull();
              assertThat(cm.getData())
                  .containsEntry(VALUE_1, VALUE_1)
                  .containsEntry(VALUE_2, VALUE_2);
              assertThat(cm.getMetadata().getOwnerReferences()).hasSize(2);
            });

    res1.getSpec().setValue(NEW_VALUE_1);
    extension.replace(res1);

    await()
        .untilAsserted(
            () -> {
              var cm =
                  extension.get(ConfigMap.class, MultipleOwnerDependentConfigMap.RESOURCE_NAME);
              assertThat(cm.getData())
                  .containsEntry(NEW_VALUE_1, NEW_VALUE_1)
                  // note that it will still contain the old value too
                  .containsEntry(VALUE_1, VALUE_1);
              assertThat(cm.getMetadata().getOwnerReferences()).hasSize(2);
            });

    res2.getSpec().setValue(NEW_VALUE_2);
    extension.replace(res2);

    await()
        .untilAsserted(
            () -> {
              var cm =
                  extension.get(ConfigMap.class, MultipleOwnerDependentConfigMap.RESOURCE_NAME);
              assertThat(cm.getData()).containsEntry(NEW_VALUE_2, NEW_VALUE_2);
              assertThat(cm.getMetadata().getOwnerReferences()).hasSize(2);
            });
  }

  MultipleOwnerDependentCustomResource testResource(String name, String value) {
    var res = new MultipleOwnerDependentCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(name).build());
    res.setSpec(new MultipleOwnerDependentSpec());
    res.getSpec().setValue(value);

    return res;
  }
}
