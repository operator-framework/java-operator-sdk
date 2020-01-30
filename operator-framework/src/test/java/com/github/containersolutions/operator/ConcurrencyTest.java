package com.github.containersolutions.operator;

import com.github.containersolutions.operator.sample.TestCustomResource;
import io.fabric8.kubernetes.api.model.ConfigMap;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.github.containersolutions.operator.IntegrationTestSupport.TEST_CUSTOM_RESOURCE_PREFIX;
import static com.github.containersolutions.operator.IntegrationTestSupport.TEST_NAMESPACE;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ConcurrencyTest {

    public static final int NUMBER_OF_RESOURCES_CREATED = 35;
    public static final int NUMBER_OF_RESOURCES_DELETED = 10;
    public static final int NUMBER_OF_RESOURCES_UPDATED = 15;
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

    @AfterAll
    public void teardown() {
        integrationTest.teardown();
    }


    @Test
    public void manyResourcesGetCreatedUpdatedAndDeleted() {
        for (int i = 0; i < NUMBER_OF_RESOURCES_CREATED; i++) {
            TestCustomResource tcr = integrationTest.createTestCustomResource(String.valueOf(i));
            integrationTest.getCrOperations().inNamespace(TEST_NAMESPACE).create(tcr);
        }

        Awaitility.await().atMost(1, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    List<ConfigMap> items = integrationTest.getK8sClient().configMaps()
                            .inNamespace(TEST_NAMESPACE)
                            .list().getItems();
                    assertThat(items).hasSize(35);
                });

        // update some resources
        for (int i = 0; i < NUMBER_OF_RESOURCES_UPDATED; i++) {
            TestCustomResource tcr = integrationTest.createTestCustomResource(String.valueOf(i));
            tcr.getSpec().setValue(i + UPDATED_SUFFIX);
            integrationTest.getCrOperations().inNamespace(TEST_NAMESPACE).createOrReplace(tcr);
        }

        Awaitility.await().atMost(1, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    List<TestCustomResource> crs = integrationTest.getCrOperations()
                            .inNamespace(TEST_NAMESPACE)
                            .list().getItems();
                    for (int i = 0; i < NUMBER_OF_RESOURCES_UPDATED; i++) {
                        final int k = i;
                        TestCustomResource testCustomResource = crs.stream().filter(c -> c.getMetadata().getName().equals(TEST_CUSTOM_RESOURCE_PREFIX + k)).findFirst().get();
                        assertThat(testCustomResource.getSpec().getValue()).isEqualTo(i + UPDATED_SUFFIX);
                    }
                });

        // deleting some resources
        for (int i = 0; i < NUMBER_OF_RESOURCES_DELETED; i++) {
            TestCustomResource tcr = integrationTest.createTestCustomResource(String.valueOf(i));
            integrationTest.getCrOperations().inNamespace(TEST_NAMESPACE).delete(tcr);
        }

        Awaitility.await().atMost(1, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    List<ConfigMap> items = integrationTest.getK8sClient().configMaps()
                            .inNamespace(TEST_NAMESPACE)
                            .list().getItems();
                    assertThat(items).hasSize(NUMBER_OF_RESOURCES_CREATED - NUMBER_OF_RESOURCES_DELETED);

                    List<TestCustomResource> crs = integrationTest.getCrOperations()
                            .inNamespace(TEST_NAMESPACE)
                            .list().getItems();
                    assertThat(crs).hasSize(NUMBER_OF_RESOURCES_CREATED - NUMBER_OF_RESOURCES_DELETED);
                });
    }


}
