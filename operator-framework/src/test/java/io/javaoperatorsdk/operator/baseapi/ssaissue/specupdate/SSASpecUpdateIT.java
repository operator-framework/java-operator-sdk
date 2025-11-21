package io.javaoperatorsdk.operator.baseapi.ssaissue.specupdate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Server-Side Apply Finalizer Removal on Spec Update",
    description =
        """
        Demonstrates an issue with Server-Side Apply (SSA) where updating the resource spec \
        without explicitly including the finalizer causes the finalizer to be removed. This \
        highlights the importance of including all desired fields when using SSA to avoid \
        unintended field removal.
        """)
class SSASpecUpdateIT {

  public static final String TEST_RESOURCE_NAME = "test";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(SSASpecUpdateReconciler.class).build();

  // showcases that if the spec of the resources is updated with SSA, but the finalizer
  // is not explicitly added to the fresh resource, the update removes the finalizer
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
