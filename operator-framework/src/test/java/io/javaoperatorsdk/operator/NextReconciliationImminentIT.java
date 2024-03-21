package io.javaoperatorsdk.operator;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.nextreconciliationimminent.NextReconciliationImminentCustomResource;
import io.javaoperatorsdk.operator.sample.nextreconciliationimminent.NextReconciliationImminentReconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class NextReconciliationImminentIT {

  private static final Logger log =
      LoggerFactory.getLogger(NextReconciliationImminentIT.class);

  public static final int WAIT_FOR_EVENT = 300;
  public static final String TEST_RESOURCE_NAME = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new NextReconciliationImminentReconciler())
          .build();

  @Test
  void skippingStatusUpdateWithNextReconciliationImminent() throws InterruptedException {
    var resource = extension.create(testResource());

    var reconciler = extension.getReconcilerOfType(NextReconciliationImminentReconciler.class);
    await().untilAsserted(() -> assertThat(reconciler.isReconciliationWaiting()).isTrue());
    Thread.sleep(WAIT_FOR_EVENT);

    resource.getMetadata().getAnnotations().put("trigger", "" + System.currentTimeMillis());
    extension.replace(resource);
    Thread.sleep(WAIT_FOR_EVENT);
    log.info("Made change to trigger event");

    reconciler.allowReconciliationToProceed();
    Thread.sleep(WAIT_FOR_EVENT);
    // second event arrived
    await().untilAsserted(() -> assertThat(reconciler.isReconciliationWaiting()).isTrue());
    reconciler.allowReconciliationToProceed();

    await().pollDelay(Duration.ofMillis(WAIT_FOR_EVENT)).untilAsserted(() -> {
      assertThat(extension.get(NextReconciliationImminentCustomResource.class, TEST_RESOURCE_NAME)
          .getStatus().getUpdateNumber()).isEqualTo(1);
    });
  }


  NextReconciliationImminentCustomResource testResource() {
    var res = new NextReconciliationImminentCustomResource();
    res.setMetadata(new ObjectMetaBuilder()
        .withName(TEST_RESOURCE_NAME)
        .build());
    return res;
  }

}
