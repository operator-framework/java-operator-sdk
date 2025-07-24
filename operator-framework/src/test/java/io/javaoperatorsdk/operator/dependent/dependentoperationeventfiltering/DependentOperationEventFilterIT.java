package io.javaoperatorsdk.operator.dependent.dependentoperationeventfiltering;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class DependentOperationEventFilterIT {

  public static final String TEST = "test";
  public static final String SPEC_VAL_1 = "val1";
  public static final String SPEC_VAL_2 = "val2";

  @RegisterExtension
  static LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withNamespaceDeleteTimeout(2)
          .withReconciler(new DependentOperationEventFilterCustomResourceTestReconciler())
          .build();

  @Test
  void reconcileNotTriggeredWithDependentResourceCreateOrUpdate() {
    var resource = operator.create(createTestResource());

    await()
        .pollDelay(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(3))
        .until(
            () ->
                ((DependentOperationEventFilterCustomResourceTestReconciler)
                            operator.getFirstReconciler())
                        .getNumberOfExecutions()
                    == 1);
    assertThat(operator.get(ConfigMap.class, TEST).getData())
        .containsEntry(ConfigMapDependentResource.KEY, SPEC_VAL_1);

    resource.getSpec().setValue(SPEC_VAL_2);
    operator.replace(resource);

    await()
        .pollDelay(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(3))
        .until(
            () ->
                ((DependentOperationEventFilterCustomResourceTestReconciler)
                            operator.getFirstReconciler())
                        .getNumberOfExecutions()
                    == 2);
    assertThat(operator.get(ConfigMap.class, TEST).getData())
        .containsEntry(ConfigMapDependentResource.KEY, SPEC_VAL_2);
  }

  private DependentOperationEventFilterCustomResource createTestResource() {
    DependentOperationEventFilterCustomResource cr =
        new DependentOperationEventFilterCustomResource();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setName(TEST);
    cr.setSpec(new DependentOperationEventFilterCustomResourceSpec());
    cr.getSpec().setValue(SPEC_VAL_1);
    return cr;
  }
}
