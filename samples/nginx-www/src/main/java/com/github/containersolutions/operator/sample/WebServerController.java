package com.github.containersolutions.operator.sample;

import com.github.containersolutions.operator.Context;
import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller(customResourceClass = WebServer.class,
        kind = WebServerController.KIND,
        group = WebServerController.GROUP,
        customResourceListClass = WebServerList.class,
        customResourceDonebaleClass = WebServerDoneable.class)
public class WebServerController implements ResourceController<WebServer> {

    static final String KIND = "WebServer";
    static final String GROUP = "sample.javaoperatorsdk";

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public Optional<WebServer> createOrUpdateResource(WebServer webServer, Context<WebServer> context) {
        log.info("Execution createOrUpdateResource for: {}", webServer.getMetadata().getName());

        String ns = webServer.getMetadata().getNamespace();

        Map<String, String> data = new HashMap<>();
        data.put("index.html", webServer.getSpec().getHtml());

        ConfigMap htmlConfigMap = new ConfigMapBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName(configMapName(webServer))
                        .withNamespace(ns)
                        .build())
                .withData(data)
                .build();

        Deployment deployment = loadYaml(Deployment.class, "deployment.yaml");
        deployment.getMetadata().setName(deploymentName(webServer));
        deployment.getMetadata().setNamespace(ns);
        deployment.getSpec().getSelector().getMatchLabels().put("app", deploymentName(webServer));
        deployment.getSpec().getTemplate().getMetadata().getLabels().put("app", deploymentName(webServer));
        deployment.getSpec().getTemplate().getSpec().getVolumes().get(0).setConfigMap(
                new ConfigMapVolumeSourceBuilder().withName(configMapName(webServer)).build());

        Service service = loadYaml(Service.class, "service.yaml");
        service.getMetadata().setName(serviceName(webServer));
        service.getMetadata().setNamespace(ns);
        service.getSpec().setSelector(deployment.getSpec().getTemplate().getMetadata().getLabels());

        ConfigMap existingConfigMap = context.getK8sClient().configMaps()
                .inNamespace(htmlConfigMap.getMetadata().getNamespace())
                .withName(htmlConfigMap.getMetadata().getName()).get();

        log.info("Creating or updating ConfigMap {}", htmlConfigMap.getMetadata().getName());
        context.getK8sClient().configMaps().inNamespace(ns).createOrReplace(htmlConfigMap);
        log.info("Creating or updating Deployment {}", deployment.getMetadata().getName());
        context.getK8sClient().apps().deployments().inNamespace(ns).createOrReplace(deployment);

        if (context.getK8sClient().services().inNamespace(ns).withName(service.getMetadata().getName()).get() == null) {
            log.info("Creating Service {}", service.getMetadata().getName());
            context.getK8sClient().services().inNamespace(ns).createOrReplace(service);
        }

        if (existingConfigMap != null) {
            if (!StringUtils.equals(existingConfigMap.getData().get("index.html"), htmlConfigMap.getData().get("index.html"))) {
                log.info("Restarting pods because HTML has changed");
                context.getK8sClient().pods().inNamespace(ns).withLabel("app", deploymentName(webServer)).delete();
            }
        }

        var status = new WebServerStatus();
        status.setHtmlConfigMap(htmlConfigMap.getMetadata().getName());
        status.setAreWeGood("Yes!");
        webServer.setStatus(status);

        return Optional.of(webServer);
    }

    @Override
    public boolean deleteResource(WebServer nginx, Context<WebServer> context) {
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

    private static String configMapName(WebServer nginx) {
        return nginx.getMetadata().getName() + "-html";
    }

    private static String deploymentName(WebServer nginx) {
        return nginx.getMetadata().getName();
    }

    private static String serviceName(WebServer nginx) {
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
