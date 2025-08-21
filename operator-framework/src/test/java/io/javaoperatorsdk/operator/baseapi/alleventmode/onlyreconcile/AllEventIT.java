package io.javaoperatorsdk.operator.baseapi.alleventmode.onlyreconcile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.alleventmode.AbstractAllEventReconciler.FINALIZER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class AllEventIT {

  public static final String TEST = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder().withReconciler(new AllEventReconciler()).build();

  @Test
  void eventsPresent() {
    var reconciler = extension.getReconcilerOfType(AllEventReconciler.class);
    extension.serverSideApply(testResource());

    await()
        .untilAsserted(
            () -> {
              assertThat(reconciler.isResourceEvent()).isTrue();
              assertThat(getResource().hasFinalizer(FINALIZER)).isTrue();
            });

    extension.delete(getResource());

    await()
        .untilAsserted(
            () -> {
              var r = getResource();
              assertThat(r).isNull();
              assertThat(reconciler.isDeleteEvent()).isTrue();
              assertThat(reconciler.isEventOnMarkedForDeletion()).isTrue();
            });
  }

  AllEventCustomResource getResource() {
    return extension.get(AllEventCustomResource.class, TEST);
  }

  AllEventCustomResource testResource() {
    var res = new AllEventCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST).build());
    return res;
  }
}
