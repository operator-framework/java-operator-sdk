package io.javaoperatorsdk.operator.sample;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusHandler;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimited;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import static io.javaoperatorsdk.operator.sample.Utils.configMapName;
import static io.javaoperatorsdk.operator.sample.Utils.createStatus;
import static io.javaoperatorsdk.operator.sample.Utils.deploymentName;
import static io.javaoperatorsdk.operator.sample.Utils.handleError;
import static io.javaoperatorsdk.operator.sample.Utils.isValidHtml;
import static io.javaoperatorsdk.operator.sample.Utils.makeDesiredIngress;
import static io.javaoperatorsdk.operator.sample.Utils.serviceName;
import static io.javaoperatorsdk.operator.sample.Utils.setInvalidHtmlErrorMessage;
import static io.javaoperatorsdk.operator.sample.Utils.simulateErrorIfRequested;
import static io.javaoperatorsdk.operator.sample.WebPageManagedDependentsReconciler.SELECTOR;

/** Shows how to implement reconciler using the low level api directly. */
@RateLimited(maxReconciliations = 2, within = 3)
@ControllerConfiguration
public class WebPageReconciler
    implements Reconciler<WebPage>, ErrorStatusHandler<WebPage>, EventSourceInitializer<WebPage> {

  public static final String INDEX_HTML = "index.html";

  private static final Logger log = LoggerFactory.getLogger(WebPageReconciler.class);

  private final KubernetesClient kubernetesClient;

  public WebPageReconciler(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
  }

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<WebPage> context) {
    var configMapEventSource =
        new InformerEventSource<>(InformerConfiguration.from(ConfigMap.class, context)
            .withLabelSelector(SELECTOR)
            .build(), context);
    var deploymentEventSource =
        new InformerEventSource<>(InformerConfiguration.from(Deployment.class, context)
            .withLabelSelector(SELECTOR)
            .build(), context);
    var serviceEventSource =
        new InformerEventSource<>(InformerConfiguration.from(Service.class, context)
            .withLabelSelector(SELECTOR)
            .build(), context);
    var ingressEventSource =
        new InformerEventSource<>(InformerConfiguration.from(Ingress.class, context)
            .withLabelSelector(SELECTOR)
            .build(), context);
    return EventSourceInitializer.nameEventSources(configMapEventSource, deploymentEventSource,
        serviceEventSource, ingressEventSource);
  }

  @Override
  public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context)
      throws Exception {
    log.info("Reconciling web page: {}", webPage);
    simulateErrorIfRequested(webPage);

    if (!isValidHtml(webPage)) {
      return UpdateControl.patchStatus(setInvalidHtmlErrorMessage(webPage));
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

    var existingIngress = context.getSecondaryResource(Ingress.class);
    if (Boolean.TRUE.equals(webPage.getSpec().getExposed())) {
      var desiredIngress = makeDesiredIngress(webPage);
      if (existingIngress.isEmpty() || !match(desiredIngress, existingIngress.get())) {
        kubernetesClient.resource(desiredIngress).inNamespace(ns).createOrReplace();
      }
    } else
      existingIngress.ifPresent(
          ingress -> kubernetesClient.resource(ingress).delete());

    // not that this is not necessary, eventually mounted config map would be updated, just this way
    // is much faster; what is handy for demo purposes.
    // https://kubernetes.io/docs/tasks/configure-pod-container/configure-pod-configmap/#mounted-configmaps-are-updated-automatically
    if (previousConfigMap != null && !StringUtils.equals(
        previousConfigMap.getData().get(INDEX_HTML),
        desiredHtmlConfigMap.getData().get(INDEX_HTML))) {
      log.info("Restarting pods because HTML has changed in {}", ns);
      kubernetesClient.pods().inNamespace(ns).withLabel("app", deploymentName(webPage)).delete();
    }
    webPage.setStatus(createStatus(desiredHtmlConfigMap.getMetadata().getName()));
    return UpdateControl.updateStatus(webPage);
  }

  private boolean match(Ingress desiredIngress, Ingress existingIngress) {
    String desiredServiceName =
        desiredIngress.getSpec().getRules().get(0).getHttp().getPaths().get(0)
            .getBackend().getService().getName();
    String existingServiceName =
        existingIngress.getSpec().getRules().get(0).getHttp().getPaths().get(0)
            .getBackend().getService().getName();
    return Objects.equals(desiredServiceName, existingServiceName);
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
    data.put(INDEX_HTML, webPage.getSpec().getHtml());
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
    labels.put(SELECTOR, "true");
    return labels;
  }

  @Override
  public ErrorStatusUpdateControl<WebPage> updateErrorStatus(
      WebPage resource, Context<WebPage> context, Exception e) {
    return handleError(resource, e);
  }
}
