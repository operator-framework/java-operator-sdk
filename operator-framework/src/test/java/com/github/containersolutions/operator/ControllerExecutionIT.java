package com.github.containersolutions.operator;

import com.github.containersolutions.operator.sample.TestCustomResource;
import com.github.containersolutions.operator.sample.TestCustomResourceSpec;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static com.github.containersolutions.operator.IntegrationTestSupport.TEST_NAMESPACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ControllerExecutionIT {

    private final static Logger log = LoggerFactory.getLogger(ControllerExecutionIT.class);
    private IntegrationTestSupport integrationTestSupport = new IntegrationTestSupport();

    @BeforeAll
    public void setup() {
        integrationTestSupport.initialize();
    }

    @BeforeEach
    public void cleanup() {
        integrationTestSupport.cleanup();
    }

    @Test
    public void configMapGetsCreatedForTestCustomResource() throws Exception {
        integrationTestSupport.teardownIfSuccess(() -> {
            TestCustomResource resource = new TestCustomResource();
            resource.setMetadata(new ObjectMetaBuilder()
                    .withName("test-custom-resource")
                    .withNamespace(TEST_NAMESPACE)
                    .build());
            resource.setKind("CustomService");
            resource.setSpec(new TestCustomResourceSpec());
            resource.getSpec().setConfigMapName("test-config-map");
            resource.getSpec().setKey("test-key");
            resource.getSpec().setValue("test-value");
            integrationTestSupport.getCrOperations().inNamespace(TEST_NAMESPACE).create(resource);

            await("configmap created").atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        ConfigMap configMap = integrationTestSupport.getK8sClient().configMaps().inNamespace(TEST_NAMESPACE)
                                .withName("test-config-map").get();
                        assertThat(configMap).isNotNull();
                        assertThat(configMap.getData().get("test-key")).isEqualTo("test-value");
                    });
            await("cr status updated").atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        TestCustomResource cr = integrationTestSupport.getCrOperations().inNamespace(TEST_NAMESPACE).withName("test-custom-resource").get();
                        assertThat(cr).isNotNull();
                        assertThat(cr.getStatus()).isNotNull();
                        assertThat(cr.getStatus().getConfigMapStatus()).isEqualTo("ConfigMap Ready");
                    });
        });
    }
}
