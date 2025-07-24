package io.javaoperatorsdk.operator.dependent.multiplemanagedexternaldependenttype;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.dependent.multiplemanageddependentsametype.MultipleManagedDependentResourceSpec;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.support.ExternalServiceMock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class MultipleManagedExternalDependentSameTypeIT {

  @RegisterExtension
  static LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new MultipleManagedExternalDependentResourceReconciler())
          .build();

  public static final String TEST_RESOURCE_NAME = "test1";
  public static final String DEFAULT_SPEC_VALUE = "val";
  public static final String UPDATED_SPEC_VALUE = "updated-val";

  protected ExternalServiceMock externalServiceMock = ExternalServiceMock.getInstance();

  @Test
  void handlesExternalCrudOperations() {
    operator.create(testResource());
    assertResourceCreatedWithData(DEFAULT_SPEC_VALUE);

    var updatedResource = testResource();
    updatedResource.getSpec().setValue(UPDATED_SPEC_VALUE);
    operator.replace(updatedResource);
    assertResourceCreatedWithData(UPDATED_SPEC_VALUE);

    operator.delete(testResource());
    assertExternalResourceDeleted();
  }

  private void assertExternalResourceDeleted() {
    await()
        .untilAsserted(
            () -> {
              var resources = externalServiceMock.listResources();
              assertThat(resources).hasSize(0);
            });
  }

  private void assertResourceCreatedWithData(String expectedData) {
    await()
        .untilAsserted(
            () -> {
              var resources = externalServiceMock.listResources();
              assertThat(resources).hasSize(2);
              assertThat(resources).allMatch(er -> er.getData().equals(expectedData));
            });
  }

  private MultipleManagedExternalDependentResourceCustomResource testResource() {
    var res = new MultipleManagedExternalDependentResourceCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());

    res.setSpec(new MultipleManagedDependentResourceSpec());
    res.getSpec().setValue(DEFAULT_SPEC_VALUE);
    return res;
  }
}
