package io.javaoperatorsdk.operator.sample;

import java.time.Duration;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryResourceIdentifier;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

import static io.javaoperatorsdk.operator.ReconcilerUtils.loadYaml;
import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_FINALIZER;
import static org.awaitility.Awaitility.await;

@ControllerConfiguration(finalizerName = NO_FINALIZER)
public class WebPageReconciler
    implements Reconciler<WebPage>, ErrorStatusHandler<WebPage>, EventSourceInitializer<WebPage> {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final KubernetesClient kubernetesClient;

  private KubernetesDependentResource<ConfigMap, WebPage> configMapDR;
  private KubernetesDependentResource<Deployment, WebPage> deploymentDR;
  private KubernetesDependentResource<Service, WebPage> serviceDR;

  public WebPageReconciler(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
    createDependentResources(kubernetesClient);
  }

  @Override
  public List<EventSource> prepareEventSources(EventSourceContext<WebPage> context) {
    return List.of(
        configMapDR.eventSource(context),
        deploymentDR.eventSource(context),
        serviceDR.eventSource(context));
  }

  @Override
  public UpdateControl<WebPage> reconcile(WebPage webPage, Context context) {
    if (webPage.getSpec().getHtml().contains("error")) {
      throw new ErrorSimulationException("Simulating error");
    }

    configMapDR.reconcile(webPage, context);
    deploymentDR.reconcile(webPage, context);
    serviceDR.reconcile(webPage, context);

    WebPageStatus status = new WebPageStatus();

    waitUntilConfigMapAvailable(webPage);
    status.setHtmlConfigMap(configMapDR.getResource(webPage).orElseThrow().getMetadata().getName());
    status.setAreWeGood("Yes!");
    status.setErrorMessage(null);
    webPage.setStatus(status);

    return UpdateControl.updateStatus(webPage);
  }

  // todo after implemented we can remove this method:
  // https://github.com/java-operator-sdk/java-operator-sdk/issues/924
  private void waitUntilConfigMapAvailable(WebPage webPage) {
    await().atMost(Duration.ofSeconds(5)).until(() -> configMapDR.getResource(webPage).isPresent());
  }

  @Override
  public Optional<WebPage> updateErrorStatus(
      WebPage resource, RetryInfo retryInfo, RuntimeException e) {
    resource.getStatus().setErrorMessage("Error: " + e.getMessage());
    return Optional.of(resource);
  }

  private void createDependentResources(KubernetesClient client) {
    this.configMapDR = new ConfigMapDependentResource();

    this.deploymentDR =
        new KubernetesDependentResource<>() {

          @Override
          protected Optional<Deployment> desired(WebPage webPage, Context context) {
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
            return Optional.of(deployment);
          }

          @Override
          protected Class<Deployment> resourceType() {
            return Deployment.class;
          }
        };

    this.serviceDR =
        new KubernetesDependentResource<>() {

          @Override
          protected Optional<Service> desired(WebPage webPage, Context context) {
            Service service = loadYaml(Service.class, getClass(), "service.yaml");
            service.getMetadata().setName(serviceName(webPage));
            service.getMetadata().setNamespace(webPage.getMetadata().getNamespace());
            Map<String, String> labels = new HashMap<>();
            labels.put("app", deploymentName(webPage));
            service.getSpec().setSelector(labels);
            return Optional.of(service);
          }

          @Override
          protected Class<Service> resourceType() {
            return Service.class;
          }
        };
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

  private class ConfigMapDependentResource extends KubernetesDependentResource<ConfigMap, WebPage>
      implements
      AssociatedSecondaryResourceIdentifier<WebPage> {

    @Override
    protected Optional<ConfigMap> desired(WebPage webPage, Context context) {
      Map<String, String> data = new HashMap<>();
      data.put("index.html", webPage.getSpec().getHtml());
      return Optional.of(new ConfigMapBuilder()
          .withMetadata(
              new ObjectMetaBuilder()
                  .withName(WebPageReconciler.configMapName(webPage))
                  .withNamespace(webPage.getMetadata().getNamespace())
                  .build())
          .withData(data)
          .build());
    }

    @Override
    protected boolean match(ConfigMap actual, ConfigMap target, Context context) {
      return StringUtils.equals(
          actual.getData().get("index.html"), target.getData().get("index.html"));
    }

    @Override
    protected ConfigMap update(
        ConfigMap actual, ConfigMap target, WebPage primary, Context context) {
      var cm = super.update(actual, target, primary, context);
      var ns = actual.getMetadata().getNamespace();
      log.info("Restarting pods because HTML has changed in {}", ns);
      kubernetesClient
          .pods()
          .inNamespace(ns)
          .withLabel("app", deploymentName(primary))
          .delete();
      return cm;
    }

    @Override
    public ResourceID associatedSecondaryID(WebPage primary) {
      return new ResourceID(configMapName(primary), primary.getMetadata().getNamespace());
    }
  }
}
