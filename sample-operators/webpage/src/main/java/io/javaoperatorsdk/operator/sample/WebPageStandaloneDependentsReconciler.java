package io.javaoperatorsdk.operator.sample;

import java.util.Arrays;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusHandler;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

import static io.javaoperatorsdk.operator.sample.Utils.createStatus;
import static io.javaoperatorsdk.operator.sample.Utils.handleError;
import static io.javaoperatorsdk.operator.sample.Utils.simulateErrorIfRequested;

/**
 * Shows how to implement reconciler using standalone dependent resources.
 */
@ControllerConfiguration(
    labelSelector = WebPageStandaloneDependentsReconciler.DEPENDENT_RESOURCE_LABEL_SELECTOR)
public class WebPageStandaloneDependentsReconciler
    implements Reconciler<WebPage>, ErrorStatusHandler<WebPage>, EventSourceInitializer<WebPage> {

  public static final String DEPENDENT_RESOURCE_LABEL_SELECTOR = "!low-level";
  private static final Logger log =
      LoggerFactory.getLogger(WebPageStandaloneDependentsReconciler.class);

  private KubernetesDependentResource<ConfigMap, WebPage> configMapDR;
  private KubernetesDependentResource<Deployment, WebPage> deploymentDR;
  private KubernetesDependentResource<Service, WebPage> serviceDR;

  public WebPageStandaloneDependentsReconciler(KubernetesClient kubernetesClient) {
    createDependentResources(kubernetesClient);
  }

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<WebPage> context) {
    return EventSourceInitializer.defaultNamedEventSources(configMapDR.initEventSource(context),
        deploymentDR.initEventSource(context), serviceDR.initEventSource(context));
  }

  @Override
  public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context)
      throws Exception {
    simulateErrorIfRequested(webPage);

    Arrays.asList(configMapDR, deploymentDR, serviceDR)
        .forEach(dr -> dr.reconcile(webPage, context));

    webPage.setStatus(
        createStatus(
            configMapDR.getResource(webPage).orElseThrow().getMetadata().getName()));
    return UpdateControl.updateStatus(webPage);
  }

  @Override
  public ErrorStatusUpdateControl<WebPage> updateErrorStatus(
      WebPage resource, Context<WebPage> retryInfo, Exception e) {
    return handleError(resource, e);
  }

  private void createDependentResources(KubernetesClient client) {
    this.configMapDR = new ConfigMapDependentResource();
    this.deploymentDR = new DeploymentDependentResource();
    this.serviceDR = new ServiceDependentResource();

    Arrays.asList(configMapDR, deploymentDR, serviceDR).forEach(dr -> {
      dr.setKubernetesClient(client);
      dr.configureWith(new KubernetesDependentResourceConfig()
          .setLabelSelector(DEPENDENT_RESOURCE_LABEL_SELECTOR));
    });
  }
}
