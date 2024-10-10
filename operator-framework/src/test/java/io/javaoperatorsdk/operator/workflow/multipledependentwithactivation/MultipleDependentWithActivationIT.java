package io.javaoperatorsdk.operator.workflow.multipledependentwithactivation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class MultipleDependentWithActivationIT {

  public static final String INITIAL_VALUE = "initial_value";
  public static final String CHANGED_VALUE = "changed_value";
  public static final String TEST_RESOURCE_NAME = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new MultipleDependentActivationReconciler())
          .build();

  @Test
  void bothDependentsWithActivationAreHandled() {
    var resource = extension.create(testResource());

    await().untilAsserted(() -> {
      var cm1 =
          extension.get(ConfigMap.class, TEST_RESOURCE_NAME + ConfigMapDependentResource1.SUFFIX);
      var cm2 =
          extension.get(ConfigMap.class, TEST_RESOURCE_NAME + ConfigMapDependentResource2.SUFFIX);
      var secret = extension.get(Secret.class, TEST_RESOURCE_NAME);
      assertThat(secret).isNotNull();
      assertThat(cm1).isNull();
      assertThat(cm2).isNull();
    });

    ActivationCondition.MET = true;
    resource.getSpec().setValue(CHANGED_VALUE);
    extension.replace(resource);

    await().untilAsserted(() -> {
      var cm1 =
          extension.get(ConfigMap.class, TEST_RESOURCE_NAME + ConfigMapDependentResource1.SUFFIX);
      var cm2 =
          extension.get(ConfigMap.class, TEST_RESOURCE_NAME + ConfigMapDependentResource2.SUFFIX);
      var secret = extension.get(Secret.class, TEST_RESOURCE_NAME);

      assertThat(secret).isNotNull();
      assertThat(cm1).isNotNull();
      assertThat(cm2).isNotNull();
      assertThat(cm1.getData()).containsEntry(ConfigMapDependentResource1.DATA_KEY,
          CHANGED_VALUE + ConfigMapDependentResource1.SUFFIX);
      assertThat(cm2.getData()).containsEntry(ConfigMapDependentResource2.DATA_KEY,
          CHANGED_VALUE + ConfigMapDependentResource2.SUFFIX);
    });

  }

  MultipleDependentActivationCustomResource testResource() {
    var res = new MultipleDependentActivationCustomResource();
    res.setMetadata(new ObjectMetaBuilder()
        .withName(TEST_RESOURCE_NAME)
        .build());
    res.setSpec(new MultipleDependentActivationSpec());
    res.getSpec().setValue(INITIAL_VALUE);

    return res;
  }


}
