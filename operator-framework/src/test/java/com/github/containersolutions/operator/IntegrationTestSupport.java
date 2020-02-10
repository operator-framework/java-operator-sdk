package com.github.containersolutions.operator;

import com.github.containersolutions.operator.api.ResourceController;
import com.github.containersolutions.operator.sample.*;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.dsl.CreateOrReplaceable;
import io.fabric8.kubernetes.client.dsl.Deletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import static com.github.containersolutions.operator.ControllerUtils.getCustomResourceDoneableClass;
import static com.github.containersolutions.operator.ControllerUtils.getCustomResourceListClass;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class IntegrationTestSupport {

    public static final String TEST_NAMESPACE = "java-operator-sdk-int-test";
    public static final String TEST_CUSTOM_RESOURCE_PREFIX = "test-custom-resource-";
    private final static Logger log = LoggerFactory.getLogger(IntegrationTestSupport.class);
    private KubernetesClient k8sClient;
    private MixedOperation<TestCustomResource, CustomResourceList, CustomResourceDoneable,
            Resource<TestCustomResource, CustomResourceDoneable>> crOperations;
    private CreateOrReplaceable<TestCustomResource, TestCustomResource, CustomResourceDoneable> createOrReplaceable;
    private Deletable<TestCustomResource> deleteable;
    private Operator operator;


    public void initialize() {
        k8sClient = new DefaultKubernetesClient();

        log.info("Running integration test in namespace " + TEST_NAMESPACE);

        CustomResourceDefinition crd = loadYaml(CustomResourceDefinition.class, "test-crd.yaml");
        k8sClient.customResourceDefinitions().createOrReplace(crd);

        if (k8sClient.namespaces().withName(TEST_NAMESPACE).get() == null) {
            k8sClient.namespaces().create(new NamespaceBuilder()
                    .withMetadata(new ObjectMetaBuilder().withName(TEST_NAMESPACE).build()).build());
        }
        operator = new Operator(k8sClient);
        operator.registerController(new TestCustomResourceController(k8sClient), TEST_NAMESPACE);
    }

    public void cleanup() {
        CustomResourceDefinition crd = loadYaml(CustomResourceDefinition.class, "test-crd.yaml");
        k8sClient.customResourceDefinitions().createOrReplace(crd);
        KubernetesDeserializer.registerCustomKind(crd.getApiVersion(), crd.getKind(), TestCustomResource.class);

        ResourceController<TestCustomResource> controller = new TestCustomResourceController(k8sClient);
        Class listClass = getCustomResourceListClass();
        Class doneableClass = getCustomResourceDoneableClass(controller);
        crOperations = k8sClient.customResources(crd, TestCustomResource.class, listClass, doneableClass);
        crOperations.inNamespace(TEST_NAMESPACE).delete(crOperations.list().getItems());
        //we depend on the actual operator from the startup to handle the finalizers and clean up
        //resources from previous test runs

        await("all CRs cleaned up").atMost(60, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThat(crOperations.inNamespace(TEST_NAMESPACE).list().getItems()).isEmpty();

                });

        k8sClient.configMaps().inNamespace(TEST_NAMESPACE)
                .withLabel("managedBy", TestCustomResourceController.class.getSimpleName())
                .delete();

        await("all config maps cleaned up").atMost(60, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThat(k8sClient.configMaps().inNamespace(TEST_NAMESPACE)
                            .withLabel("managedBy", TestCustomResourceController.class.getSimpleName())
                            .list().getItems().isEmpty());
                });

        log.info("Cleaned up namespace " + TEST_NAMESPACE);
    }

    public void teardown() {
        operator.stop();
    }

    private <T> T loadYaml(Class<T> clazz, String yaml) {
        try (InputStream is = getClass().getResourceAsStream(yaml)) {
            return Serialization.unmarshal(is, clazz);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot find yaml on classpath: " + yaml);
        }
    }

    public TestCustomResource createTestCustomResource(String id) {
        TestCustomResource resource = new TestCustomResource();
        resource.setMetadata(new ObjectMetaBuilder()
                .withName(TEST_CUSTOM_RESOURCE_PREFIX + id)
                .withNamespace(TEST_NAMESPACE)
                .build());
        resource.setKind("CustomService");
        resource.setSpec(new TestCustomResourceSpec());
        resource.getSpec().setConfigMapName("test-config-map-" + id);
        resource.getSpec().setKey("test-key");
        resource.getSpec().setValue(id);
        return resource;
    }

    public KubernetesClient getK8sClient() {
        return k8sClient;
    }

    public MixedOperation<TestCustomResource, CustomResourceList, CustomResourceDoneable, Resource<TestCustomResource, CustomResourceDoneable>> getCrOperations() {
        return crOperations;
    }

    public Operator getOperator() {
        return operator;
    }
}
