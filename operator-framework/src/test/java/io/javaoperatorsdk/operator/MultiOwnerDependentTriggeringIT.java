package io.javaoperatorsdk.operator;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.multipleupdateondependent.MultipleOwnerDependentConfigMap;
import io.javaoperatorsdk.operator.sample.multipleupdateondependent.MultipleOwnerDependentCustomResource;
import io.javaoperatorsdk.operator.sample.multipleupdateondependent.MultipleOwnerDependentReconciler;
import io.javaoperatorsdk.operator.sample.multipleupdateondependent.MultipleOwnerDependentSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class MultiOwnerDependentTriggeringIT {

  public static final String VALUE_1 = "value1";
  public static final String VALUE_2 = "value2";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withConfigurationService(o -> o.withDefaultNonSSAResource(Set.of()))
          .withReconciler(MultipleOwnerDependentReconciler.class)
          .build();


  @Test
  void multiOwnerTriggeringAndManagement() {
    extension.create(testResource("res1", VALUE_1));
    extension.create(testResource("res2", VALUE_2));

    await().untilAsserted(() -> {
      var cm = extension.get(ConfigMap.class, MultipleOwnerDependentConfigMap.RESOURCE_NAME);

      assertThat(cm).isNotNull();
      assertThat(cm.getData())
          .containsEntry(VALUE_1, VALUE_1)
          .containsEntry(VALUE_2, VALUE_2);
      assertThat(cm.getMetadata().getOwnerReferences()).hasSize(2);
    });
    // todo triggering
  }

  MultipleOwnerDependentCustomResource testResource(String name, String value) {
    var res = new MultipleOwnerDependentCustomResource();
    res.setMetadata(new ObjectMetaBuilder()
        .withName(name)
        .build());
    res.setSpec(new MultipleOwnerDependentSpec());
    res.getSpec().setValue(value);

    return res;
  }

}
