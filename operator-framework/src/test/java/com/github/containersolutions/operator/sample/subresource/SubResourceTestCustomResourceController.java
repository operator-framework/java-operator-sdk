package com.github.containersolutions.operator.sample.subresource;

import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.CustomResourceClientAware;
import com.github.containersolutions.operator.api.ResourceController;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Controller(
        crdName = SubResourceTestCustomResourceController.CRD_NAME,
        customResourceClass = SubResourceTestCustomResource.class)
public class SubResourceTestCustomResourceController implements ResourceController<SubResourceTestCustomResource>, CustomResourceClientAware<SubResourceTestCustomResource> {

    public static final String CRD_NAME = "customservices2.sample.javaoperatorsdk";
    private static final Logger log = LoggerFactory.getLogger(SubResourceTestCustomResourceController.class);
    private final KubernetesClient kubernetesClient;
    private final boolean updateStatus;
    private MixedOperation<SubResourceTestCustomResource, KubernetesResourceList<SubResourceTestCustomResource>,
            Doneable<SubResourceTestCustomResource>, Resource<SubResourceTestCustomResource, Doneable<SubResourceTestCustomResource>>> customResourceClient;
    private AtomicInteger numberOfExecutions = new AtomicInteger(0);

    public SubResourceTestCustomResourceController(KubernetesClient kubernetesClient) {
        this(kubernetesClient, true);
    }

    public SubResourceTestCustomResourceController(KubernetesClient kubernetesClient, boolean updateStatus) {
        this.kubernetesClient = kubernetesClient;
        this.updateStatus = updateStatus;
    }

    @Override
    public boolean deleteResource(SubResourceTestCustomResource resource) {
        kubernetesClient.configMaps().inNamespace(resource.getMetadata().getNamespace())
                .withName(resource.getSpec().getConfigMapName()).delete();
        log.info("Deleting config map with name: {} for resource: {}", resource.getSpec().getConfigMapName(), resource.getMetadata().getName());
        return true;
    }

    @Override
    public Optional<SubResourceTestCustomResource> createOrUpdateResource(SubResourceTestCustomResource resource) {
        numberOfExecutions.addAndGet(1);
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
            labels.put("managedBy", SubResourceTestCustomResourceController.class.getSimpleName());
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
            SubResourceTestCustomResourceStatus status = resource.getStatus();
            if (status == null) {
                status = new SubResourceTestCustomResourceStatus();
                resource.setStatus(status);
            }
            status.setConfigMapStatus("ConfigMap Ready");
            customResourceClient.updateStatus(resource);
        }
        return Optional.empty();
    }

    private Map<String, String> configMapData(SubResourceTestCustomResource resource) {
        Map<String, String> data = new HashMap<>();
        data.put(resource.getSpec().getKey(), resource.getSpec().getValue());
        return data;
    }

    public int getNumberOfExecutions() {
        return numberOfExecutions.get();
    }

    @Override
    public void setCustomResourceClient(MixedOperation<SubResourceTestCustomResource, KubernetesResourceList<SubResourceTestCustomResource>,
            Doneable<SubResourceTestCustomResource>, Resource<SubResourceTestCustomResource, Doneable<SubResourceTestCustomResource>>> client) {
        this.customResourceClient = client;
    }
}
