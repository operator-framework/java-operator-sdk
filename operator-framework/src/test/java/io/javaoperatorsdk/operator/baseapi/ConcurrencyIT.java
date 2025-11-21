package io.javaoperatorsdk.operator.baseapi;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.baseapi.simple.TestCustomResource;
import io.javaoperatorsdk.operator.baseapi.simple.TestReconciler;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.support.TestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Concurrent Reconciliation of Multiple Resources",
    description =
        """
        Demonstrates the operator's ability to handle concurrent reconciliation of multiple \
        resources. The test creates, updates, and deletes many resources simultaneously to \
        verify proper handling of concurrent operations, ensuring thread safety and correct \
        resource state management under load.
        """)
class ConcurrencyIT {
  public static final int NUMBER_OF_RESOURCES_CREATED = 50;
  public static final int NUMBER_OF_RESOURCES_DELETED = 30;
  public static final int NUMBER_OF_RESOURCES_UPDATED = 20;
  public static final String UPDATED_SUFFIX = "_updated";
  private static final Logger log = LoggerFactory.getLogger(ConcurrencyIT.class);

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(new TestReconciler(true)).build();

  @Test
  void manyResourcesGetCreatedUpdatedAndDeleted() throws InterruptedException {
    log.info("Creating {} new resources", NUMBER_OF_RESOURCES_CREATED);
    for (int i = 0; i < NUMBER_OF_RESOURCES_CREATED; i++) {
      TestCustomResource tcr = TestUtils.testCustomResourceWithPrefix(String.valueOf(i));
      operator.resources(TestCustomResource.class).resource(tcr).create();
    }

    await()
        .atMost(1, TimeUnit.MINUTES)
        .untilAsserted(
            () -> {
              List<ConfigMap> items =
                  operator
                      .resources(ConfigMap.class)
                      .withLabel("managedBy", TestReconciler.class.getSimpleName())
                      .list()
                      .getItems();
              assertThat(items).hasSize(NUMBER_OF_RESOURCES_CREATED);
            });

    log.info("Updating {} resources", NUMBER_OF_RESOURCES_UPDATED);
    // update some resources
    for (int i = 0; i < NUMBER_OF_RESOURCES_UPDATED; i++) {
      TestCustomResource tcr =
          operator.get(TestCustomResource.class, TestUtils.TEST_CUSTOM_RESOURCE_PREFIX + i);
      tcr.getSpec().setValue(i + UPDATED_SUFFIX);
      operator.resources(TestCustomResource.class).resource(tcr).createOrReplace();
    }
    // sleep for a short time to make variability to the test, so some updates are not
    // executed before delete
    Thread.sleep(300);

    log.info("Deleting {} resources", NUMBER_OF_RESOURCES_DELETED);
    for (int i = 0; i < NUMBER_OF_RESOURCES_DELETED; i++) {
      TestCustomResource tcr = TestUtils.testCustomResourceWithPrefix(String.valueOf(i));
      operator.resources(TestCustomResource.class).resource(tcr).delete();
    }

    await()
        .atMost(1, TimeUnit.MINUTES)
        .untilAsserted(
            () -> {
              List<ConfigMap> items =
                  operator
                      .resources(ConfigMap.class)
                      .withLabel("managedBy", TestReconciler.class.getSimpleName())
                      .list()
                      .getItems();
              // reducing configmaps to names only - better for debugging
              List<String> itemDescs =
                  items.stream()
                      .map(configMap -> configMap.getMetadata().getName())
                      .collect(Collectors.toList());
              assertThat(itemDescs)
                  .hasSize(NUMBER_OF_RESOURCES_CREATED - NUMBER_OF_RESOURCES_DELETED);

              List<TestCustomResource> crs =
                  operator.resources(TestCustomResource.class).list().getItems();
              assertThat(crs).hasSize(NUMBER_OF_RESOURCES_CREATED - NUMBER_OF_RESOURCES_DELETED);
            });
  }
}
