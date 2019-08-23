package com.github.containersolutions.operator.sample;

import com.github.containersolutions.operator.Context;
import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller(customResourceClass = NginxWww.class,
        kind = NginxWwwController.KIND,
        group = NginxWwwController.GROUP,
        customResourceListClass = NginxWwwList.class,
        customResourceDonebaleClass = NginxWwwDoneable.class)
public class NginxWwwController implements ResourceController<NginxWww> {

    static final String KIND = "NginxWww";
    static final String GROUP = "sample.javaoperatorsdk";

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public Optional<NginxWww> createOrUpdateResource(NginxWww nginx, Context<NginxWww> context) {
        log.info("Execution createOrUpdateResource for: {}", nginx.getMetadata().getName());

        String ns = nginx.getMetadata().getNamespace();

        Map<String, String> data = new HashMap<>();
        data.put("index.html", nginx.getSpec().getHtml());

        ConfigMap htmlConfigMap = new ConfigMapBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName(configMapName(nginx))
                        .withNamespace(ns)
                        .build())
                .withData(data)
                .build();

        Deployment deployment = loadYaml(Deployment.class, "nginx-deployment.yaml");
        deployment.getMetadata().setName(deploymentName(nginx));
        deployment.getMetadata().setNamespace(ns);
        deployment.getSpec().getSelector().getMatchLabels().put("app", deploymentName(nginx));
        deployment.getSpec().getTemplate().getMetadata().getLabels().put("app", deploymentName(nginx));
        deployment.getSpec().getTemplate().getSpec().getVolumes().get(0).setConfigMap(
                new ConfigMapVolumeSourceBuilder().withName(configMapName(nginx)).build());

        Service service = loadYaml(Service.class, "nginx-service.yaml");
        service.getMetadata().setName(serviceName(nginx));
        service.getMetadata().setNamespace(ns);
        service.getSpec().setSelector(deployment.getSpec().getTemplate().getMetadata().getLabels());

        ConfigMap existingConfigMap = context.getK8sClient().configMaps()
                .inNamespace(htmlConfigMap.getMetadata().getNamespace())
                .withName(htmlConfigMap.getMetadata().getName()).get();

        log.info("Creating or updating ConfigMap {}", htmlConfigMap.getMetadata().getName());
        context.getK8sClient().configMaps().inNamespace(ns).createOrReplace(htmlConfigMap);
        log.info("Creating or updating Deployment {}", deployment.getMetadata().getName());
        context.getK8sClient().apps().deployments().inNamespace(ns).createOrReplace(deployment);
        log.info("Creating or updating Service {}", service.getMetadata().getName());
        context.getK8sClient().services().inNamespace(ns).createOrReplace(service);

        if (existingConfigMap != null) {
            if (!StringUtils.equals(existingConfigMap.getData().get("index.html"), htmlConfigMap.getData().get("index.html"))) {
                log.info("Restarting pods because HTML has changed");
                context.getK8sClient().pods().inNamespace(ns).withLabel("app", deploymentName(nginx)).delete();
            }
        }

        var status = new NginxWwwStatus();
        status.setHtmlConfigMap(htmlConfigMap.getMetadata().getName());
        status.setAreWeGood("Yes!");
        nginx.setStatus(status);

        return Optional.of(nginx);
    }

    @Override
    public boolean deleteResource(NginxWww nginx, Context<NginxWww> context) {
        log.info("Execution deleteResource for: {}", nginx.getMetadata().getName());

        log.info("Deleting ConfigMap {}", configMapName(nginx));
        var configMap = context.getK8sClient().configMaps()
                .inNamespace(nginx.getMetadata().getNamespace())
                .withName(configMapName(nginx));
        if (configMap.get() != null) {
            configMap.delete();
        }

        log.info("Deleting Deployment {}", deploymentName(nginx));
        var deployment = context.getK8sClient().apps().deployments()
                .inNamespace(nginx.getMetadata().getNamespace())
                .withName(deploymentName(nginx));
        if (deployment.get() != null) {
            deployment.cascading(true).delete();
        }

        log.info("Deleting Service {}", serviceName(nginx));
        var service = context.getK8sClient().services()
                .inNamespace(nginx.getMetadata().getNamespace())
                .withName(serviceName(nginx));
        if (service.get() != null) {
            service.delete();
        }
        return true;
    }

    private static String configMapName(NginxWww nginx) {
        return nginx.getMetadata().getName() + "-html";
    }

    private static String deploymentName(NginxWww nginx) {
        return nginx.getMetadata().getName();
    }

    private static String serviceName(NginxWww nginx) {
        return nginx.getMetadata().getName();
    }

    private <T> T loadYaml(Class<T> clazz, String yaml) {
        try (InputStream is = getClass().getResourceAsStream(yaml)) {
            return Serialization.unmarshal(is, clazz);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot find yaml on classpath: " + yaml);
        }
    }
}
