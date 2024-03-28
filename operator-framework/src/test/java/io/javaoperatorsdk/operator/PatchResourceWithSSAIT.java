package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.patchresourcewithssa.PatchResourceWithSSACustomResource;
import io.javaoperatorsdk.operator.sample.patchresourcewithssa.PatchResourceWithSSAReconciler;
import io.javaoperatorsdk.operator.sample.patchresourcewithssa.PatchResourceWithSSASpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class PatchResourceWithSSAIT {

  public static final String RESOURCE_NAME = "test1";
  public static final String INIT_VALUE = "init value";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new PatchResourceWithSSAReconciler())
          .build();

  @Test
  void reconcilerPatchesResourceWithSSA() {
    extension.create(testResource());

    await().untilAsserted(() -> {
      var actualResource = extension.get(PatchResourceWithSSACustomResource.class, RESOURCE_NAME);

      assertThat(actualResource.getSpec().getInitValue()).isEqualTo(INIT_VALUE);
      assertThat(actualResource.getSpec().getControllerManagedValue())
          .isEqualTo(PatchResourceWithSSAReconciler.ADDED_VALUE);
      // finalizer is added to the SSA patch in the background by the framework
      assertThat(actualResource.getMetadata().getFinalizers()).isNotEmpty();
      assertThat(actualResource.getStatus().isSuccessfullyReconciled()).isTrue();
    });
  }

  PatchResourceWithSSACustomResource testResource() {
    var res = new PatchResourceWithSSACustomResource();
    res.setMetadata(new ObjectMetaBuilder()
        .withName(RESOURCE_NAME)
        .build());
    res.setSpec(new PatchResourceWithSSASpec());
    res.getSpec().setInitValue(INIT_VALUE);
    return res;
  }
}
