package com.github.containersolutions.operator.sample;

import com.github.containersolutions.operator.TestExecutionInfoProvider;
import com.github.containersolutions.operator.api.Context;
import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import com.github.containersolutions.operator.api.UpdateControl;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Controller(
        generationAwareEventProcessing = false,
        crdName = TestCustomResourceController.CRD_NAME,
        customResourceClass = TestCustomResource.class)
public class TestCustomResourceController implements ResourceController<TestCustomResource>, TestExecutionInfoProvider {

    private static final Logger log = LoggerFactory.getLogger(TestCustomResourceController.class);

    public static final String CRD_NAME = "customservices.sample.javaoperatorsdk";

    private final KubernetesClient kubernetesClient;
    private final boolean updateStatus;
    private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

    public TestCustomResourceController(KubernetesClient kubernetesClient) {
        this(kubernetesClient, true);
    }

    public TestCustomResourceController(KubernetesClient kubernetesClient, boolean updateStatus) {
        this.kubernetesClient = kubernetesClient;
        this.updateStatus = updateStatus;
    }

    @Override
    public boolean deleteResource(TestCustomResource resource, Context<TestCustomResource> context) {
        Boolean delete = kubernetesClient.configMaps().inNamespace(resource.getMetadata().getNamespace())
                .withName(resource.getSpec().getConfigMapName()).delete();
        if (delete) {
            log.info("Deleted ConfigMap {} for resource: {}", resource.getSpec().getConfigMapName(), resource.getMetadata().getName());
        } else {
            log.error("Failed to delete ConfigMap {} for resource: {}", resource.getSpec().getConfigMapName(), resource.getMetadata().getName());
        }
        return true;
    }

    @Override
    public UpdateControl<TestCustomResource> createOrUpdateResource(TestCustomResource resource,
                                                                    Context<TestCustomResource> context) {
        numberOfExecutions.addAndGet(1);
        if (!resource.getMetadata().getFinalizers().contains(Controller.DEFAULT_FINALIZER)) {
            throw new IllegalStateException("Finalizer is not present.");
        }

        ConfigMap existingConfigMap = kubernetesClient
                .configMaps().inNamespace(resource.getMetadata().getNamespace())
                .withName(resource.getSpec().getConfigMapName()).get();

        if (existingConfigMap != null) {
            existingConfigMap.setData(configMapData(resource));
//            existingConfigMap.getMetadata().setResourceVersion(null);
            kubernetesClient.configMaps().inNamespace(resource.getMetadata().getNamespace())
                    .withName(existingConfigMap.getMetadata().getName()).createOrReplace(existingConfigMap);
        } else {
            Map<String, String> labels = new HashMap<>();
            labels.put("managedBy", TestCustomResourceController.class.getSimpleName());
            ConfigMap newConfigMap = new ConfigMapBuilder()
                    .withMetadata(new ObjectMetaBuilder()
                            .withName(resource.getSpec().getConfigMapName())
                            .withNamespace(resource.getMetadata().getNamespace())
                            .withLabels(labels)
                            .build())
                    .withData(configMapData(resource)).build();
            kubernetesClient.configMaps().inNamespace(resource.getMetadata().getNamespace())
                    .createOrReplace(newConfigMap);
        }
        if (updateStatus) {
            if (resource.getStatus() == null) {
                resource.setStatus(new TestCustomResourceStatus());
            }
            resource.getStatus().setConfigMapStatus("ConfigMap Ready");
        }
        return UpdateControl.updateCustomResource(resource);
    }

    private Map<String, String> configMapData(TestCustomResource resource) {
        Map<String, String> data = new HashMap<>();
        data.put(resource.getSpec().getKey(), resource.getSpec().getValue());
        return data;
    }

    public int getNumberOfExecutions() {
        return numberOfExecutions.get();
    }
}
