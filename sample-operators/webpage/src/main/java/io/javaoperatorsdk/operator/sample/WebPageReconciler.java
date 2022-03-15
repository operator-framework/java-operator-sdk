package io.javaoperatorsdk.operator.sample;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

/** Shows how to implement reconciler using the low level api directly. */
@ControllerConfiguration(
    labelSelector = WebPageReconciler.LOW_LEVEL_LABEL_KEY)
public class WebPageReconciler
    implements Reconciler<WebPage>, ErrorStatusHandler<WebPage>, EventSourceInitializer<WebPage> {

  public static final String LOW_LEVEL_LABEL_KEY = "low-level";
  public static final String INDEX_HTML = "index.html";

  private static final Logger log = LoggerFactory.getLogger(WebPageReconciler.class);

  private final KubernetesClient kubernetesClient;

  public WebPageReconciler(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
  }

  InformerEventSource configMapEventSource;

  @Override
  public List<EventSource> prepareEventSources(EventSourceContext<WebPage> context) {
    configMapEventSource =
        new InformerEventSource<>(InformerConfiguration.from(context, ConfigMap.class)
            .withLabelSelector(LOW_LEVEL_LABEL_KEY)
            .build(), context);
    var deploymentEventSource =
        new InformerEventSource<>(InformerConfiguration.from(context, Deployment.class)
            .withLabelSelector(LOW_LEVEL_LABEL_KEY)
            .build(), context);
    var serviceEventSource =
        new InformerEventSource<>(InformerConfiguration.from(context, Service.class)
            .withLabelSelector(LOW_LEVEL_LABEL_KEY)
            .build(), context);
    return List.of(configMapEventSource, deploymentEventSource, serviceEventSource);
  }

  @Override
  public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context) {
    log.info("Reconciling web page: {}", webPage);
    if (webPage.getSpec().getHtml().contains("error")) {
      throw new ErrorSimulationException("Simulating error");
    }
    String ns = webPage.getMetadata().getNamespace();
    String configMapName = configMapName(webPage);
    String deploymentName = deploymentName(webPage);


    ConfigMap desiredHtmlConfigMap = makeDesiredHtmlConfigMap(ns, configMapName, webPage);
    Deployment desiredDeployment =
        makeDesiredDeployment(webPage, deploymentName, ns, configMapName);
    Service desiredService = makeDesiredService(webPage, ns, desiredDeployment);

    var previousConfigMap = context.getSecondaryResource(ConfigMap.class).orElse(null);
    if (!match(desiredHtmlConfigMap, previousConfigMap)) {
      log.info(
          "Creating or updating ConfigMap {} in {}",
          desiredHtmlConfigMap.getMetadata().getName(),
          ns);
      kubernetesClient.configMaps().inNamespace(ns).createOrReplace(desiredHtmlConfigMap);
    }

    var existingDeployment = context.getSecondaryResource(Deployment.class).orElse(null);
    if (!match(desiredDeployment, existingDeployment)) {
      log.info(
          "Creating or updating Deployment {} in {}",
          desiredDeployment.getMetadata().getName(),
          ns);
      kubernetesClient.apps().deployments().inNamespace(ns).createOrReplace(desiredDeployment);
    }

    var existingService = context.getSecondaryResource(Service.class).orElse(null);
    if (!match(desiredService, existingService)) {
      log.info(
          "Creating or updating Deployment {} in {}",
          desiredDeployment.getMetadata().getName(),
          ns);
      kubernetesClient.services().inNamespace(ns).createOrReplace(desiredService);
    }

    if (previousConfigMap != null && !StringUtils.equals(
        previousConfigMap.getData().get(INDEX_HTML),
        desiredHtmlConfigMap.getData().get(INDEX_HTML))) {
      log.info("Restarting pods because HTML has changed in {}", ns);
      kubernetesClient.pods().inNamespace(ns).withLabel("app", deploymentName(webPage)).delete();
    }
    webPage.setStatus(createStatus(desiredHtmlConfigMap.getMetadata().getName()));
    return UpdateControl.updateStatus(webPage);
  }

  private WebPageStatus createStatus(String configMapName) {
    WebPageStatus status = new WebPageStatus();
    status.setHtmlConfigMap(configMapName);
    status.setAreWeGood(true);
    status.setErrorMessage(null);
    return status;
  }

  private boolean match(Deployment desiredDeployment, Deployment deployment) {
    if (deployment == null) {
      return false;
    } else {
      return desiredDeployment.getSpec().getReplicas().equals(deployment.getSpec().getReplicas()) &&
          desiredDeployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage()
              .equals(
                  deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
    }
  }

  private boolean match(Service desiredService, Service service) {
    if (service == null) {
      return false;
    }
    return desiredService.getSpec().getSelector().equals(service.getSpec().getSelector());
  }

  private boolean match(ConfigMap desiredHtmlConfigMap, ConfigMap existingConfigMap) {
    if (existingConfigMap == null) {
      return false;
    } else {
      return desiredHtmlConfigMap.getData().equals(existingConfigMap.getData());
    }
  }

  private Service makeDesiredService(WebPage webPage, String ns, Deployment desiredDeployment) {
    Service desiredService = ReconcilerUtils.loadYaml(Service.class, getClass(), "service.yaml");
    desiredService.getMetadata().setName(serviceName(webPage));
    desiredService.getMetadata().setNamespace(ns);
    desiredService.getMetadata().setLabels(lowLevelLabel());
    desiredService
        .getSpec()
        .setSelector(desiredDeployment.getSpec().getTemplate().getMetadata().getLabels());
    desiredService.addOwnerReference(webPage);
    return desiredService;
  }

  private Deployment makeDesiredDeployment(WebPage webPage, String deploymentName, String ns,
      String configMapName) {
    Deployment desiredDeployment =
        ReconcilerUtils.loadYaml(Deployment.class, getClass(), "deployment.yaml");
    desiredDeployment.getMetadata().setName(deploymentName);
    desiredDeployment.getMetadata().setNamespace(ns);
    desiredDeployment.getMetadata().setLabels(lowLevelLabel());
    desiredDeployment.getSpec().getSelector().getMatchLabels().put("app", deploymentName);
    desiredDeployment.getSpec().getTemplate().getMetadata().getLabels().put("app", deploymentName);
    desiredDeployment
        .getSpec()
        .getTemplate()
        .getSpec()
        .getVolumes()
        .get(0)
        .setConfigMap(new ConfigMapVolumeSourceBuilder().withName(configMapName).build());
    desiredDeployment.addOwnerReference(webPage);
    return desiredDeployment;
  }

  private ConfigMap makeDesiredHtmlConfigMap(String ns, String configMapName, WebPage webPage) {
    Map<String, String> data = new HashMap<>();
    data.put("index.html", webPage.getSpec().getHtml());
    ConfigMap configMap =
        new ConfigMapBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName(configMapName)
                    .withNamespace(ns)
                    .withLabels(lowLevelLabel())
                    .build())
            .withData(data)
            .build();
    configMap.addOwnerReference(webPage);
    return configMap;
  }

  public static Map<String, String> lowLevelLabel() {
    Map<String, String> labels = new HashMap<>();
    labels.put(LOW_LEVEL_LABEL_KEY, "true");
    return labels;
  }

  private static String configMapName(WebPage nginx) {
    return nginx.getMetadata().getName() + "-html";
  }

  private static String deploymentName(WebPage nginx) {
    return nginx.getMetadata().getName();
  }

  private static String serviceName(WebPage nginx) {
    return nginx.getMetadata().getName();
  }

  @Override
  public ErrorStatusUpdateControl<WebPage> updateErrorStatus(
      WebPage resource, Context<WebPage> context, Exception e) {
    resource.getStatus().setErrorMessage("Error: " + e.getMessage());
    return ErrorStatusUpdateControl.updateStatus(resource);
  }
}
