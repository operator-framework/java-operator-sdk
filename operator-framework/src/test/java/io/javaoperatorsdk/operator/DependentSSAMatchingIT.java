package io.javaoperatorsdk.operator;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.dependentssa.DependentSSAReconciler;
import io.javaoperatorsdk.operator.sample.dependentssa.DependentSSASpec;
import io.javaoperatorsdk.operator.sample.dependentssa.DependnetSSACustomResource;
import io.javaoperatorsdk.operator.sample.dependentssa.SSAConfigMapDependent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class DependentSSAMatchingIT {

  public static final String TEST_RESOURCE_NAME = "test1";
  public static final String INITIAL_VALUE = "INITIAL_VALUE";
  public static final String CHANGED_VALUE = "CHANGED_VALUE";

  public static final String CUSTOM_FIELD_MANAGER_NAME = "customFieldManagerName";
  public static final String OTHER_FIELD_MANAGER = "otherFieldManager";
  public static final String ADDITIONAL_KEY = "key2";
  public static final String ADDITIONAL_VALUE = "Additional Value";


  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new DependentSSAReconciler(),
              o -> o.withFieldManager(CUSTOM_FIELD_MANAGER_NAME))
          .build();

  @Test
  void testMatchingAndUpdate() {
    SSAConfigMapDependent.NUMBER_OF_UPDATES.set(0);
    var resource = extension.create(testResource());

    await().untilAsserted(() -> {
      var cm = extension.get(ConfigMap.class, TEST_RESOURCE_NAME);
      assertThat(cm).isNotNull();
      assertThat(cm.getData()).containsEntry(SSAConfigMapDependent.DATA_KEY, INITIAL_VALUE);
      assertThat(cm.getMetadata().getManagedFields().stream()
          .filter(fm -> fm.getManager().equals(CUSTOM_FIELD_MANAGER_NAME))).isNotEmpty();
      assertThat(SSAConfigMapDependent.NUMBER_OF_UPDATES.get()).isZero();
    });

    ConfigMap cmPatch = new ConfigMapBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .withName(TEST_RESOURCE_NAME)
            .withNamespace(resource.getMetadata().getNamespace())
            .build())
        .withData(Map.of(ADDITIONAL_KEY, ADDITIONAL_VALUE))
        .build();

    extension.getKubernetesClient().configMaps().resource(cmPatch).patch(new PatchContext.Builder()
        .withFieldManager(OTHER_FIELD_MANAGER)
        .withPatchType(PatchType.SERVER_SIDE_APPLY)
        .build());

    await().pollDelay(Duration.ofMillis(300)).untilAsserted(() -> {
      var cm = extension.get(ConfigMap.class, TEST_RESOURCE_NAME);
      assertThat(cm.getData()).hasSize(2);
      assertThat(SSAConfigMapDependent.NUMBER_OF_UPDATES.get()).isZero();
      assertThat(cm.getMetadata().getManagedFields()).hasSize(2);
    });

    resource.getSpec().setValue(CHANGED_VALUE);
    extension.replace(resource);

    await().untilAsserted(() -> {
      var cm = extension.get(ConfigMap.class, TEST_RESOURCE_NAME);
      assertThat(cm.getData()).hasSize(2);
      assertThat(cm.getData()).containsEntry(SSAConfigMapDependent.DATA_KEY, CHANGED_VALUE);
      assertThat(cm.getData()).containsEntry(ADDITIONAL_KEY, ADDITIONAL_VALUE);
      assertThat(cm.getMetadata().getManagedFields()).hasSize(2);
      assertThat(SSAConfigMapDependent.NUMBER_OF_UPDATES.get()).isEqualTo(1);
    });
  }

  public DependnetSSACustomResource testResource() {
    DependnetSSACustomResource resource = new DependnetSSACustomResource();
    resource.setMetadata(new ObjectMetaBuilder()
        .withName(TEST_RESOURCE_NAME)
        .build());
    resource.setSpec(new DependentSSASpec());
    resource.getSpec().setValue(INITIAL_VALUE);
    return resource;
  }

}
