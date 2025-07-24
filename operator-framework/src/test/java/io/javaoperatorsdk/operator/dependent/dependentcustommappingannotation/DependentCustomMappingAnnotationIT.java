package io.javaoperatorsdk.operator.dependent.dependentcustommappingannotation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.dependent.dependentcustommappingannotation.CustomMappingConfigMapDependentResource.CUSTOM_NAMESPACE_KEY;
import static io.javaoperatorsdk.operator.dependent.dependentcustommappingannotation.CustomMappingConfigMapDependentResource.CUSTOM_NAME_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class DependentCustomMappingAnnotationIT {

  public static final String INITIAL_VALUE = "initial value";
  public static final String CHANGED_VALUE = "changed value";
  public static final String TEST_RESOURCE_NAME = "test1";

  @RegisterExtension
  static LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(DependentCustomMappingReconciler.class)
          .build();

  @Test
  void testCustomMappingAnnotationForDependent() {
    var cr = extension.create(testResource());
    assertConfigMapData(INITIAL_VALUE);

    cr.getSpec().setValue(CHANGED_VALUE);
    cr = extension.replace(cr);
    assertConfigMapData(CHANGED_VALUE);

    extension.delete(cr);

    await()
        .untilAsserted(
            () -> {
              var resource = extension.get(ConfigMap.class, TEST_RESOURCE_NAME);
              assertThat(resource).isNull();
            });
  }

  private void assertConfigMapData(String val) {
    await()
        .untilAsserted(
            () -> {
              var resource = extension.get(ConfigMap.class, TEST_RESOURCE_NAME);
              assertThat(resource).isNotNull();
              assertThat(resource.getMetadata().getAnnotations())
                  .containsKey(CUSTOM_NAME_KEY)
                  .containsKey(CUSTOM_NAMESPACE_KEY);
              assertThat(resource.getData())
                  .containsEntry(CustomMappingConfigMapDependentResource.KEY, val);
            });
  }

  DependentCustomMappingCustomResource testResource() {
    var dr = new DependentCustomMappingCustomResource();
    dr.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    dr.setSpec(new DependentCustomMappingSpec());
    dr.getSpec().setValue(INITIAL_VALUE);

    return dr;
  }
}
