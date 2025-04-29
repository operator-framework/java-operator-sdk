package io.javaoperatorsdk.operator.baseapi.concurrentfinalizerremoval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ConcurrentFinalizerRemovalIT {

  private static final Logger log = LoggerFactory.getLogger(ConcurrentFinalizerRemovalIT.class);
  public static final String TEST_RESOURCE_NAME = "test";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          // should work without a retry, thus not retry the whole reconciliation but to retry
          // finalizer removal only. 
          .withReconciler(
              new ConcurrentFinalizerRemovalReconciler1(),
              o ->
                  o.withRetry(GenericRetry.noRetry()).withFinalizer("reconciler1.sample/finalizer"))
          .withReconciler(
              new ConcurrentFinalizerRemovalReconciler2(),
              o ->
                  o.withRetry(GenericRetry.noRetry()).withFinalizer("reconciler2.sample/finalizer"))
          .build();

  @Test
  void concurrentFinalizerRemoval() {
    for (int i = 0; i < 10; i++) {
      var resource = extension.create(createResource());
      await()
          .untilAsserted(
              () -> {
                var res =
                    extension.get(
                        ConcurrentFinalizerRemovalCustomResource.class, TEST_RESOURCE_NAME);
                assertThat(res.getMetadata().getFinalizers()).hasSize(2);
              });
      resource.getMetadata().setResourceVersion(null);
      extension.delete(resource);

      await()
          .untilAsserted(
              () -> {
                var res =
                    extension.get(
                        ConcurrentFinalizerRemovalCustomResource.class, TEST_RESOURCE_NAME);
                assertThat(res).isNull();
              });
    }
  }

  public ConcurrentFinalizerRemovalCustomResource createResource() {
    ConcurrentFinalizerRemovalCustomResource res = new ConcurrentFinalizerRemovalCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    res.setSpec(new ConcurrentFinalizerRemovalSpec());
    res.getSpec().setNumber(0);
    return res;
  }
}
