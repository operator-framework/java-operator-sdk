package com.github.containersolutions.operator;

import com.github.containersolutions.operator.sample.TestCustomResource;
import com.github.containersolutions.operator.sample.TestCustomResourceSpec;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ConcurrencyTest {

    private IntegrationTest integrationTest = new IntegrationTest();

    @BeforeAll
    public void setup() {
        integrationTest.setup();
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
    public void manyResourcesGetCreatedAndUpdated() {
        for (int i = 0; i < 35; i++) {
            TestCustomResource tcr = createTCR(String.valueOf(i));
            integrationTest.crOperations.inNamespace(IntegrationTest.TEST_NAMESPACE).create(tcr);
//            if (i % 3 == 0) {
//                TestCustomResource newTcr = createTCR(String.valueOf(i));
//                integrationTest.crOperations.inNamespace(IntegrationTest.TEST_NAMESPACE).createOrReplace(newTcr);
//            }
        }
        Awaitility.await().atMost(1, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    List<ConfigMap> items = integrationTest.k8sClient.configMaps()
                            .inNamespace(IntegrationTest.TEST_NAMESPACE)
                            .list().getItems();
                    assertThat(items).hasSize(35);
                });
    }


    @Test
    public void manyResourcesGetCreatedUpdatedAndDeleted() {
        for (int i = 0; i < 35; i++) {
            TestCustomResource tcr = createTCR(String.valueOf(i));
            integrationTest.crOperations.inNamespace(IntegrationTest.TEST_NAMESPACE).create(tcr);
//            if (i % 3 == 0) {
//                TestCustomResource newTcr = createTCR(String.valueOf(i));
//                integrationTest.crOperations.inNamespace(IntegrationTest.TEST_NAMESPACE).createOrReplace(newTcr);
//            }
        }

        Awaitility.await().atMost(1, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    List<ConfigMap> items = integrationTest.k8sClient.configMaps()
                            .inNamespace(IntegrationTest.TEST_NAMESPACE)
                            .list().getItems();
                    assertThat(items).hasSize(35);
                });

        for (int i = 0; i < 10; i++) {
            TestCustomResource tcr = createTCR(String.valueOf(i));
            integrationTest.crOperations.inNamespace(IntegrationTest.TEST_NAMESPACE).delete(tcr);
        }

        Awaitility.await().atMost(1, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    List<ConfigMap> items = integrationTest.k8sClient.configMaps()
                            .inNamespace(IntegrationTest.TEST_NAMESPACE)
                            .list().getItems();
                    assertThat(items).hasSize(25);
                });
    }

    private TestCustomResource createTCR(String id) {
        TestCustomResource resource = new TestCustomResource();
        resource.setMetadata(new ObjectMetaBuilder()
                .withName("test-custom-resource-" + id)
                .withNamespace(IntegrationTest.TEST_NAMESPACE)
                .build());
        resource.setKind("CustomService");
        resource.setSpec(new TestCustomResourceSpec());
        resource.getSpec().setConfigMapName("test-config-map-" + id);
        resource.getSpec().setKey("test-key");
        resource.getSpec().setValue(id);
        return resource;
    }
}
