package io.javaoperatorsdk.operator.baseapi.patchresourcewithssa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public abstract class PatchWithSSAITBase {

  public static final String RESOURCE_NAME = "test1";
  public static final String INIT_VALUE = "init value";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder().withReconciler(reconciler()).build();

  @Test
  void reconcilerPatchesResourceWithSSA() {
    extension.create(testResource());

    await()
        .untilAsserted(
            () -> {
              var actualResource =
                  extension.get(PatchResourceWithSSACustomResource.class, RESOURCE_NAME);

              assertThat(actualResource.getSpec().getInitValue()).isEqualTo(INIT_VALUE);
              assertThat(actualResource.getSpec().getControllerManagedValue())
                  .isEqualTo(PatchResourceWithSSAReconciler.ADDED_VALUE);
              // finalizer is added to the SSA patch in the background by the framework
              assertThat(actualResource.getMetadata().getFinalizers()).isNotEmpty();
              assertThat(actualResource.getStatus().isSuccessfullyReconciled()).isTrue();
              // one for resource, one for subresource
              assertThat(
                      actualResource.getMetadata().getManagedFields().stream()
                          .filter(
                              mf ->
                                  mf.getManager()
                                      .equals(
                                          reconciler().getClass().getSimpleName().toLowerCase()))
                          .toList())
                  .hasSize(2);
            });
  }

  protected abstract Reconciler<?> reconciler();

  PatchResourceWithSSACustomResource testResource() {
    var res = new PatchResourceWithSSACustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME).build());
    res.setSpec(new PatchResourceWithSSASpec());
    res.getSpec().setInitValue(INIT_VALUE);
    return res;
  }
}
