package io.javaoperatorsdk.operator.sample;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

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
    List<EventSource> eventSources = new ArrayList<>(3);
    configMapDR.eventSource(context).ifPresent(es -> eventSources.add(es));
    deploymentDR.eventSource(context).ifPresent(es -> eventSources.add(es));
    serviceDR.eventSource(context).ifPresent(es -> eventSources.add(es));
    return eventSources;
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
    status.setHtmlConfigMap(configMapDR.getResource(webPage).get().getMetadata().getName());
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
    this.configMapDR =
        new KubernetesDependentResource<>(
            client,
            ConfigMap.class,
            (WebPage webPage, Context context) -> {
              Map<String, String> data = new HashMap<>();
              data.put("index.html", webPage.getSpec().getHtml());
              return new ConfigMapBuilder()
                  .withMetadata(
                      new ObjectMetaBuilder()
                          .withName(configMapName(webPage))
                          .withNamespace(webPage.getMetadata().getNamespace())
                          .build())
                  .withData(data)
                  .build();
            }) {
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
        };
    configMapDR.setAssociatedSecondaryResourceIdentifier(
        primary -> new ResourceID(configMapName(primary), primary.getMetadata().getNamespace()));

    this.deploymentDR =
        new KubernetesDependentResource<>(
            client,
            Deployment.class,
            (webPage, context) -> {
              var deploymentName = deploymentName(webPage);
              Deployment deployment = loadYaml(Deployment.class, "deployment.yaml");
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
            }) {
          @Override
          protected boolean match(Deployment actual, Deployment target, Context context) {
            // todo comparator
            return true;
          }
        };

    this.serviceDR =
        new KubernetesDependentResource<>(
            client,
            Service.class,
            (webPage, context) -> {
              Service service = loadYaml(Service.class, "service.yaml");
              service.getMetadata().setName(serviceName(webPage));
              service.getMetadata().setNamespace(webPage.getMetadata().getNamespace());
              Map<String, String> labels = new HashMap<>();
              labels.put("app", deploymentName(webPage));
              service.getSpec().setSelector(labels);
              return service;
            }) {

          protected boolean match(Service actual, Service target, Context context) {
            // todo comparator
            return true;
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

  private <T> T loadYaml(Class<T> clazz, String yaml) {
    try (InputStream is = getClass().getResourceAsStream(yaml)) {
      return Serialization.unmarshal(is, clazz);
    } catch (IOException ex) {
      throw new IllegalStateException("Cannot find yaml on classpath: " + yaml);
    }
  }
}
