package io.javaoperatorsdk.operator.dependent.multipledrsametypenodiscriminator;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.dependent.multipledrsametypenodiscriminator.MultipleManagedDependentSameTypeNoDiscriminatorReconciler.DATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class MultipleManagedDependentNoDiscriminatorIT {

  public static final String RESOURCE_NAME = "test1";
  public static final String INITIAL_VALUE = "initial_value";
  public static final String CHANGED_VALUE = "changed_value";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new MultipleManagedDependentSameTypeNoDiscriminatorReconciler())
          .build();

  @Test
  void handlesCRUDOperations() {
    var res = extension.create(testResource());

    await()
        .untilAsserted(
            () -> {
              var cm1 =
                  extension.get(
                      ConfigMap.class,
                      RESOURCE_NAME
                          + MultipleManagedDependentNoDiscriminatorConfigMap1.NAME_SUFFIX);
              var cm2 =
                  extension.get(
                      ConfigMap.class,
                      RESOURCE_NAME
                          + MultipleManagedDependentNoDiscriminatorConfigMap2.NAME_SUFFIX);

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
              var cm1 =
                  extension.get(
                      ConfigMap.class,
                      RESOURCE_NAME
                          + MultipleManagedDependentNoDiscriminatorConfigMap1.NAME_SUFFIX);
              var cm2 =
                  extension.get(
                      ConfigMap.class,
                      RESOURCE_NAME
                          + MultipleManagedDependentNoDiscriminatorConfigMap2.NAME_SUFFIX);

              assertThat(cm1.getData()).containsEntry(DATA_KEY, CHANGED_VALUE);
              assertThat(cm2.getData()).containsEntry(DATA_KEY, CHANGED_VALUE);
            });

    extension.delete(res);

    await()
        .timeout(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              var cm1 =
                  extension.get(
                      ConfigMap.class,
                      RESOURCE_NAME
                          + MultipleManagedDependentNoDiscriminatorConfigMap1.NAME_SUFFIX);
              var cm2 =
                  extension.get(
                      ConfigMap.class,
                      RESOURCE_NAME
                          + MultipleManagedDependentNoDiscriminatorConfigMap2.NAME_SUFFIX);

              assertThat(cm1).isNull();
              assertThat(cm2).isNull();
            });
  }

  MultipleManagedDependentNoDiscriminatorCustomResource testResource() {
    var res = new MultipleManagedDependentNoDiscriminatorCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME).build());
    res.setSpec(new MultipleManagedDependentNoDiscriminatorSpec());
    res.getSpec().setValue(INITIAL_VALUE);
    return res;
  }
}
