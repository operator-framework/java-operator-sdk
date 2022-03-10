package io.javaoperatorsdk.operator.sample;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CrudKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryResourceIdentifier;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

import static io.javaoperatorsdk.operator.ReconcilerUtils.loadYaml;
import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_FINALIZER;

/**
 * Shows how to implement reconciler using standalone dependent resources.
 */
@ControllerConfiguration(finalizerName = NO_FINALIZER,
    labelSelector = WebPageReconcilerDependentResources.DEPENDENT_RESOURCE_LABEL_SELECTOR)
public class WebPageReconcilerDependentResources
    implements Reconciler<WebPage>, ErrorStatusHandler<WebPage>, EventSourceInitializer<WebPage> {

  public static final String DEPENDENT_RESOURCE_LABEL_SELECTOR = "!low-level";
  private static final Logger log =
      LoggerFactory.getLogger(WebPageReconcilerDependentResources.class);
  private final KubernetesClient kubernetesClient;

  private KubernetesDependentResource<ConfigMap, WebPage> configMapDR;
  private KubernetesDependentResource<Deployment, WebPage> deploymentDR;
  private KubernetesDependentResource<Service, WebPage> serviceDR;

  public WebPageReconcilerDependentResources(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
    createDependentResources(kubernetesClient);
  }

  @Override
  public List<EventSource> prepareEventSources(EventSourceContext<WebPage> context) {
    return List.of(
        configMapDR.initEventSource(context),
        deploymentDR.initEventSource(context),
        serviceDR.initEventSource(context));
  }

  @Override
  public UpdateControl<WebPage> reconcile(WebPage webPage, Context context) {
    if (webPage.getSpec().getHtml().contains("error")) {
      // special case just to showcase error if doing a demo
      throw new ErrorSimulationException("Simulating error");
    }

    configMapDR.reconcile(webPage, context);
    deploymentDR.reconcile(webPage, context);
    serviceDR.reconcile(webPage, context);

    webPage.setStatus(
        createStatus(configMapDR.getResource(webPage).orElseThrow().getMetadata().getName()));
    return UpdateControl.updateStatus(webPage);
  }

  private WebPageStatus createStatus(String configMapName) {
    WebPageStatus status = new WebPageStatus();
    status.setHtmlConfigMap(configMapName);
    status.setAreWeGood(true);
    status.setErrorMessage(null);
    return status;
  }

  @Override
  public Optional<WebPage> updateErrorStatus(
      WebPage resource, RetryInfo retryInfo, RuntimeException e) {
    resource.getStatus().setErrorMessage("Error: " + e.getMessage());
    return Optional.of(resource);
  }

  private void createDependentResources(KubernetesClient client) {
    this.configMapDR = new ConfigMapDependentResource();
    this.configMapDR.setKubernetesClient(client);
    configMapDR.configureWith(new KubernetesDependentResourceConfig()
        .setLabelSelector(DEPENDENT_RESOURCE_LABEL_SELECTOR));

    this.deploymentDR =
        new CrudKubernetesDependentResource<>() {

          @Override
          protected Deployment desired(WebPage webPage, Context context) {
            var deploymentName = deploymentName(webPage);
            Deployment deployment = loadYaml(Deployment.class, getClass(), "deployment.yaml");
            deployment.getMetadata().setName(deploymentName);
            deployment.getMetadata().setNamespace(webPage.getMetadata().getNamespace());
            deployment.getSpec().getSelector().getMatchLabels().put("app", deploymentName);

            deployment
                .getSpec()
                .getTemplate()
                .getMetadata()
                .getLabels()
                .put("app", deploymentName);
            deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getVolumes()
                .get(0)
                .setConfigMap(
                    new ConfigMapVolumeSourceBuilder().withName(configMapName(webPage)).build());
            return deployment;
          }

          @Override
          protected Class<Deployment> resourceType() {
            return Deployment.class;
          }
        };
    deploymentDR.setKubernetesClient(client);
    deploymentDR.configureWith(new KubernetesDependentResourceConfig()
        .setLabelSelector(DEPENDENT_RESOURCE_LABEL_SELECTOR));

    this.serviceDR =
        new CrudKubernetesDependentResource<>() {

          @Override
          protected Service desired(WebPage webPage, Context<WebPage> context) {
            Service service = loadYaml(Service.class, getClass(), "service.yaml");
            service.getMetadata().setName(serviceName(webPage));
            service.getMetadata().setNamespace(webPage.getMetadata().getNamespace());
            Map<String, String> labels = new HashMap<>();
            labels.put("app", deploymentName(webPage));
            service.getSpec().setSelector(labels);
            return service;
          }

          @Override
          protected Class<Service> resourceType() {
            return Service.class;
          }
        };
    serviceDR.setKubernetesClient(client);
    serviceDR.configureWith(new KubernetesDependentResourceConfig()
        .setLabelSelector(DEPENDENT_RESOURCE_LABEL_SELECTOR));
  }

  public static String configMapName(WebPage nginx) {
    return nginx.getMetadata().getName() + "-html";
  }

  public static String deploymentName(WebPage nginx) {
    return nginx.getMetadata().getName();
  }

  public static String serviceName(WebPage webPage) {
    return webPage.getMetadata().getName();
  }

  private class ConfigMapDependentResource
      extends CrudKubernetesDependentResource<ConfigMap, WebPage>
      implements
      AssociatedSecondaryResourceIdentifier<WebPage> {

    @Override
    protected ConfigMap desired(WebPage webPage, Context context) {
      Map<String, String> data = new HashMap<>();
      data.put("index.html", webPage.getSpec().getHtml());
      return new ConfigMapBuilder()
          .withMetadata(
              new ObjectMetaBuilder()
                  .withName(WebPageReconcilerDependentResources.configMapName(webPage))
                  .withNamespace(webPage.getMetadata().getNamespace())
                  .build())
          .withData(data)
          .build();
    }

    @Override
    public ConfigMap update(ConfigMap actual, ConfigMap target, WebPage primary, Context context) {
      var res = super.update(actual, target, primary, context);
      var ns = actual.getMetadata().getNamespace();
      log.info("Restarting pods because HTML has changed in {}", ns);
      kubernetesClient
          .pods()
          .inNamespace(ns)
          .withLabel("app", deploymentName(primary))
          .delete();
      return res;
    }

    @Override
    public ResourceID associatedSecondaryID(WebPage primary) {
      return new ResourceID(configMapName(primary), primary.getMetadata().getNamespace());
    }
  }
}
