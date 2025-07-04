package io.javaoperatorsdk.operator.baseapi.ssaissue.specupdate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SSASpecUpdateIT {

  public static final String TEST_RESOURCE_NAME = "test";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(SSASpecUpdateReconciler.class).build();

  @Test
  void showFinalizerRemovalWhenSpecUpdated() {
    SSASpecUpdateCustomResource res = createResource();
    operator.create(res);

    await()
        .untilAsserted(
            () -> {
              var actual = operator.get(SSASpecUpdateCustomResource.class, TEST_RESOURCE_NAME);
              assertThat(actual.getSpec()).isNotNull();
              assertThat(actual.getFinalizers()).isEmpty();
            });
  }

  SSASpecUpdateCustomResource createResource() {
    SSASpecUpdateCustomResource res = new SSASpecUpdateCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    return res;
  }
}
