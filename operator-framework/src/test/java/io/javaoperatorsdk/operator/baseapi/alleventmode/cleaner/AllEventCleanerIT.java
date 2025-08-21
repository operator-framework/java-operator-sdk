package io.javaoperatorsdk.operator.baseapi.alleventmode.cleaner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.alleventmode.AbstractAllEventReconciler.FINALIZER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class AllEventCleanerIT {

  public static final String TEST = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder().withReconciler(new AllEventCleanerReconciler()).build();

  // todo delete event without finalizer
  @Test
  void eventsPresent() {
    var reconciler = extension.getReconcilerOfType(AllEventCleanerReconciler.class);
    extension.serverSideApply(testResource());

    await()
        .untilAsserted(
            () -> {
              assertThat(reconciler.isResourceEventPresent()).isTrue();
              assertThat(getResource().hasFinalizer(FINALIZER)).isTrue();
            });

    extension.delete(getResource());

    await()
        .untilAsserted(
            () -> {
              var r = getResource();
              assertThat(r).isNull();
              assertThat(reconciler.isDeleteEventPresent()).isTrue();
              assertThat(reconciler.isEventOnMarkedForDeletion()).isTrue();
            });
  }

  AllEventCleanerCustomResource getResource() {
    return extension.get(AllEventCleanerCustomResource.class, TEST);
  }

  AllEventCleanerCustomResource testResource() {
    var res = new AllEventCleanerCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST).build());
    return res;
  }
}
