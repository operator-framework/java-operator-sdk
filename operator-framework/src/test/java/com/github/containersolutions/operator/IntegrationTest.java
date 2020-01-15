package com.github.containersolutions.operator;

import com.github.containersolutions.operator.sample.*;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import org.junit.jupiter.api.*;
import org.mockito.internal.matchers.Any;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class IntegrationTest {

    public static final String TEST_NAMESPACE = "java-operator-sdk-int-test";

    public final KubernetesClient k8sClient = new DefaultKubernetesClient();
    public MixedOperation<TestCustomResource, TestCustomResourceList, TestCustomResourceDoneable, Resource<TestCustomResource, TestCustomResourceDoneable>> crOperations;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private Operator operator;

    @BeforeAll
    public void setup() {
        log.info("Running integration test in namespace " + TEST_NAMESPACE);

        if (k8sClient.namespaces().withName(TEST_NAMESPACE).get() == null) {
            k8sClient.namespaces().create(new NamespaceBuilder()
                    .withMetadata(new ObjectMetaBuilder().withName(TEST_NAMESPACE).build()).build());
        }

        operator = new Operator(k8sClient);
        operator.registerController(new TestCustomResourceController());

    }

    @BeforeEach
    public void cleanup() {

        CustomResourceDefinition crd = loadYaml(CustomResourceDefinition.class, "test-crd.yaml");
        k8sClient.customResourceDefinitions().createOrReplace(crd);
        KubernetesDeserializer.registerCustomKind(crd.getApiVersion(), crd.getKind(), TestCustomResource.class);

        k8sClient.configMaps().inNamespace(TEST_NAMESPACE)
                .withLabel("managedBy", TestCustomResourceController.class.getSimpleName())
                .delete();

        crOperations = k8sClient.customResources(crd, TestCustomResource.class, TestCustomResourceList.class, TestCustomResourceDoneable.class);
        crOperations.inNamespace(TEST_NAMESPACE).delete(crOperations.list().getItems());
        //we depend on the actual operator from the startup to handle the finalizers and clean up
        //resources from previous test runs

        await("all resources cleaned up").atMost(60, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThat(crOperations.inNamespace(TEST_NAMESPACE).list().getItems()).isEmpty();
                    assertThat(k8sClient.configMaps().inNamespace(TEST_NAMESPACE).list().getItems()).isEmpty();
                });

        log.info("Cleaned up namespace " + TEST_NAMESPACE);
    }

    @AfterAll
    public void teardown() {
//        CustomResourceDefinition crd = loadYaml(CustomResourceDefinition.class, "test-crd.yaml");
//        k8sClient.customResourceDefinitions().delete(crd);
        operator.stop();
    }

    @Test
    public void configMapGetsCreatedForTestCustomResource() {
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
        crOperations.inNamespace(TEST_NAMESPACE).create(resource);

        await("configmap created").atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    ConfigMap configMap = k8sClient.configMaps().inNamespace(TEST_NAMESPACE)
                            .withName("test-config-map").get();
                    assertThat(configMap).isNotNull();
                    assertThat(configMap.getData().get("test-key")).isEqualTo("test-value");
                });
        await("cr status updated").atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    TestCustomResource cr = crOperations.inNamespace(TEST_NAMESPACE).withName("test-custom-resource").get();
                    assertThat(cr).isNotNull();
                    assertThat(cr.getStatus()).isNotNull();
                    assertThat(cr.getStatus().getConfigMapStatus()).isEqualTo("ConfigMap Ready");
                });
    }

    private <T> T loadYaml(Class<T> clazz, String yaml) {
        try (InputStream is = getClass().getResourceAsStream(yaml)) {
            return Serialization.unmarshal(is, clazz);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot find yaml on classpath: " + yaml);
        }
    }
}
