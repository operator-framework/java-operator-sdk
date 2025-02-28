package io.javaoperatorsdk.operator.dependent.externalstate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.support.ExternalIDGenServiceMock;

import static io.javaoperatorsdk.operator.dependent.externalstate.ExternalStateReconciler.ID_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ExternalStateIT {

  private static final String TEST_RESOURCE_NAME = "test1";

  public static final String INITIAL_TEST_DATA = "initialTestData";
  public static final String UPDATED_DATA = "updatedData";

  private final ExternalIDGenServiceMock externalService = ExternalIDGenServiceMock.getInstance();

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(ExternalStateReconciler.class).build();

  @Test
  public void reconcilesResourceWithPersistentState() {
    var resource = operator.create(testResource());
    assertResourcesCreated(resource, INITIAL_TEST_DATA);

    resource.getSpec().setData(UPDATED_DATA);
    operator.replace(resource);
    assertResourcesCreated(resource, UPDATED_DATA);

    operator.delete(resource);
    assertResourcesDeleted(resource);
  }

  private void assertResourcesDeleted(ExternalStateCustomResource resource) {
    await()
        .untilAsserted(
            () -> {
              var cm = operator.get(ConfigMap.class, resource.getMetadata().getName());
              var resources = externalService.listResources();
              assertThat(cm).isNull();
              assertThat(resources).isEmpty();
            });
  }

  private void assertResourcesCreated(
      ExternalStateCustomResource resource, String initialTestData) {
    await()
        .untilAsserted(
            () -> {
              var cm = operator.get(ConfigMap.class, resource.getMetadata().getName());
              var resources = externalService.listResources();
              assertThat(resources).hasSize(1);
              var extRes = externalService.listResources().get(0);
              assertThat(extRes.getData()).isEqualTo(initialTestData);
              assertThat(cm).isNotNull();
              assertThat(cm.getData().get(ID_KEY)).isEqualTo(extRes.getId());
            });
  }

  private ExternalStateCustomResource testResource() {
    var res = new ExternalStateCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());

    res.setSpec(new ExternalStateSpec());
    res.getSpec().setData(INITIAL_TEST_DATA);
    return res;
  }
}
