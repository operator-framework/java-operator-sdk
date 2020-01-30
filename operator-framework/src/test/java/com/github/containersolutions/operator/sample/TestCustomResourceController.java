package com.github.containersolutions.operator.sample;

import com.github.containersolutions.operator.api.Context;
import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller(
        crdName = TestCustomResourceController.CRD_NAME,
        group = TestCustomResourceController.TEST_GROUP,
        kind = TestCustomResourceController.KIND_NAME,
        customResourceClass = TestCustomResource.class,
        customResourceListClass = TestCustomResourceList.class,
        customResourceDonebaleClass = TestCustomResourceDoneable.class)
public class TestCustomResourceController implements ResourceController<TestCustomResource> {

    private static final Logger log = LoggerFactory.getLogger(TestCustomResourceController.class);

    public static final String KIND_NAME = "CustomService";
    public static final String TEST_GROUP = "sample.javaoperatorsdk";
    public static final String CRD_NAME = "customservices.sample.javaoperatorsdk";

    @Override
    public boolean deleteResource(TestCustomResource resource, Context<TestCustomResource> context) {
        context.getK8sClient().configMaps().inNamespace(resource.getMetadata().getNamespace())
                .withName(resource.getSpec().getConfigMapName()).delete();
        log.info("Deleting config map with name: {} for resource: {}", resource.getSpec().getConfigMapName(), resource.getMetadata().getName());
        return true;
    }

    @Override
    public Optional<TestCustomResource> createOrUpdateResource(TestCustomResource resource, Context<TestCustomResource> context) {
        ConfigMap existingConfigMap = context.getK8sClient()
                .configMaps().inNamespace(resource.getMetadata().getNamespace())
                .withName(resource.getSpec().getConfigMapName()).get();

        if (existingConfigMap != null) {
            existingConfigMap.setData(configMapData(resource));
//            existingConfigMap.getMetadata().setResourceVersion(null);
            context.getK8sClient().configMaps().inNamespace(resource.getMetadata().getNamespace())
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
            context.getK8sClient().configMaps().inNamespace(resource.getMetadata().getNamespace())
                    .createOrReplace(newConfigMap);
        }

        if (resource.getStatus() == null) {
            resource.setStatus(new TestCustomResourceStatus());
        }
        resource.getStatus().setConfigMapStatus("ConfigMap Ready");
        return Optional.of(resource);
    }

    private Map<String, String> configMapData(TestCustomResource resource) {
        Map<String, String> data = new HashMap<>();
        data.put(resource.getSpec().getKey(), resource.getSpec().getValue());
        return data;
    }
}
