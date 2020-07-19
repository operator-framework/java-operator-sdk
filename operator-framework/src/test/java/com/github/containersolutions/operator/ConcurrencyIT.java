package com.github.containersolutions.operator;

import com.github.containersolutions.operator.sample.TestCustomResource;
import com.github.containersolutions.operator.sample.TestCustomResourceController;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
        KubernetesClient k8sClient = new DefaultKubernetesClient();
        integrationTest.initialize(k8sClient, new TestCustomResourceController(k8sClient, true),
                "test-crd.yaml");
    }

    @BeforeEach
    public void cleanup() {
        integrationTest.cleanup();
    }

    @Test
    public void manyResourcesGetCreatedUpdatedAndDeleted() {
        integrationTest.teardownIfSuccess(() -> {
            log.info("Creating {} new resources", NUMBER_OF_RESOURCES_CREATED);
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

            log.info("Updating {} resources", NUMBER_OF_RESOURCES_UPDATED);
            // update some resources
            for (int i = 0; i < NUMBER_OF_RESOURCES_UPDATED; i++) {
                TestCustomResource tcr = (TestCustomResource) integrationTest.getCrOperations()
                        .inNamespace(TEST_NAMESPACE)
                        .withName(IntegrationTestSupport.TEST_CUSTOM_RESOURCE_PREFIX + i)
                        .get();
                tcr.getSpec().setValue(i + UPDATED_SUFFIX);
                integrationTest.getCrOperations().inNamespace(TEST_NAMESPACE).createOrReplace(tcr);
            }
            // sleep for a short time to make variability to the test, so some updates are not executed before delete
            Thread.sleep(300);

            log.info("Deleting {} resources", NUMBER_OF_RESOURCES_DELETED);
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
                        //reducing configmaps to names only - better for debugging
                        List<String> itemDescs = items.stream().map(configMap -> configMap.getMetadata().getName()).collect(Collectors.toList());
                        assertThat(itemDescs).hasSize(NUMBER_OF_RESOURCES_CREATED - NUMBER_OF_RESOURCES_DELETED);

                        List<TestCustomResource> crs = integrationTest.getCrOperations()
                                .inNamespace(TEST_NAMESPACE)
                                .list().getItems();
                        assertThat(crs).hasSize(NUMBER_OF_RESOURCES_CREATED - NUMBER_OF_RESOURCES_DELETED);
                    });
        });
    }


}
