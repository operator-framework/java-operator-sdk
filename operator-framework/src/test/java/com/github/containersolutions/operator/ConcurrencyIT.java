package com.github.containersolutions.operator;

import com.github.containersolutions.operator.sample.TestCustomResource;
import com.github.containersolutions.operator.sample.TestCustomResourceController;
import io.fabric8.kubernetes.api.model.ConfigMap;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.github.containersolutions.operator.IntegrationTestSupport.TEST_NAMESPACE;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ConcurrencyIT {

    public static final int NUMBER_OF_RESOURCES_CREATED = 50;
    public static final int NUMBER_OF_RESOURCES_DELETED = 30;
    public static final int NUMBER_OF_RESOURCES_UPDATED = 20;
    private static final Logger log = LoggerFactory.getLogger(ConcurrencyIT.class);
    public static final String UPDATED_SUFFIX = "_updated";
    private IntegrationTestSupport integrationTest = new IntegrationTestSupport();

    @BeforeAll
    public void setup() {
        integrationTest.initialize();
    }

    @BeforeEach
    public void cleanup() {
        integrationTest.cleanup();
    }

    @Test
    public void manyResourcesGetCreatedUpdatedAndDeleted() throws Exception {
        integrationTest.teardownIfSuccess(() -> {
            log.info("Adding new resources.");
            for (int i = 0; i < NUMBER_OF_RESOURCES_CREATED; i++) {
                TestCustomResource tcr = integrationTest.createTestCustomResource(String.valueOf(i));
                integrationTest.getCrOperations().inNamespace(TEST_NAMESPACE).create(tcr);
            }

            Awaitility.await().atMost(1, TimeUnit.MINUTES)
                    .untilAsserted(() -> {
                        List<ConfigMap> items = integrationTest.getK8sClient().configMaps()
                                .inNamespace(TEST_NAMESPACE)
                                .withLabel("managedBy", TestCustomResourceController.class.getSimpleName())
                                .list().getItems();
                        assertThat(items).hasSize(NUMBER_OF_RESOURCES_CREATED);
                    });

            log.info("Updating resources.");
            // update some resources
            for (int i = 0; i < NUMBER_OF_RESOURCES_UPDATED; i++) {
                TestCustomResource tcr = integrationTest.createTestCustomResource(String.valueOf(i));
                tcr.getSpec().setValue(i + UPDATED_SUFFIX);
                integrationTest.getCrOperations().inNamespace(TEST_NAMESPACE).createOrReplace(tcr);
            }
            // sleep to make some variability to the test, so some updates are not executed before delete
            Thread.sleep(300);

            log.info("Deleting resources.");
            // deleting some resources
            for (int i = 0; i < NUMBER_OF_RESOURCES_DELETED; i++) {
                TestCustomResource tcr = integrationTest.createTestCustomResource(String.valueOf(i));
                integrationTest.getCrOperations().inNamespace(TEST_NAMESPACE).delete(tcr);
            }

            Awaitility.await().atMost(1, TimeUnit.MINUTES)
                    .untilAsserted(() -> {
                        List<ConfigMap> items = integrationTest.getK8sClient().configMaps()
                                .inNamespace(TEST_NAMESPACE)
                                .withLabel("managedBy", TestCustomResourceController.class.getSimpleName())
                                .list().getItems();
                        assertThat(items).hasSize(NUMBER_OF_RESOURCES_CREATED - NUMBER_OF_RESOURCES_DELETED);

                        List<TestCustomResource> crs = integrationTest.getCrOperations()
                                .inNamespace(TEST_NAMESPACE)
                                .list().getItems();
                        assertThat(crs).hasSize(NUMBER_OF_RESOURCES_CREATED - NUMBER_OF_RESOURCES_DELETED);
                    });
        });
    }


}
