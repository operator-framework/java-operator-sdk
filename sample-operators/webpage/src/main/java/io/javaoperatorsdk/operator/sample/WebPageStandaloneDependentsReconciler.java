package io.javaoperatorsdk.operator.sample;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfigBuilder;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.sample.customresource.WebPage;
import io.javaoperatorsdk.operator.sample.dependentresource.ConfigMapDependentResource;
import io.javaoperatorsdk.operator.sample.dependentresource.DeploymentDependentResource;
import io.javaoperatorsdk.operator.sample.dependentresource.IngressDependentResource;
import io.javaoperatorsdk.operator.sample.dependentresource.ServiceDependentResource;

import static io.javaoperatorsdk.operator.sample.Utils.*;
import static io.javaoperatorsdk.operator.sample.WebPageManagedDependentsReconciler.SELECTOR;

/**
 * Shows how to implement reconciler using standalone dependent resources.
 */
@ControllerConfiguration
public class WebPageStandaloneDependentsReconciler
    implements Reconciler<WebPage>, ErrorStatusHandler<WebPage>, EventSourceInitializer<WebPage> {

  private final ConfigMapDependentResource configMapDR;
  private final DeploymentDependentResource deploymentDR;
  private final ServiceDependentResource serviceDR;
  private final IngressDependentResource ingressDR;
  @SuppressWarnings("rawtypes")
  public static final KubernetesDependentResourceConfig config =
      new KubernetesDependentResourceConfigBuilder().withLabelSelector(SELECTOR + "=true").build();

  public WebPageStandaloneDependentsReconciler(KubernetesClient client) {
    this.configMapDR = new ConfigMapDependentResource();
    configureDR(configMapDR, client);
    this.deploymentDR = new DeploymentDependentResource();
    configureDR(deploymentDR, client);
    this.serviceDR = new ServiceDependentResource();
    configureDR(serviceDR, client);
    this.ingressDR = new IngressDependentResource();
    configureDR(ingressDR, client);
  }

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<WebPage> context) {
    return EventSourceInitializer.nameEventSourcesFromDependentResource(context, configMapDR,
        deploymentDR, serviceDR, ingressDR);
  }

  @Override
  public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context)
      throws Exception {
    simulateErrorIfRequested(webPage);

    if (!isValidHtml(webPage)) {
      return UpdateControl.patchStatus(setInvalidHtmlErrorMessage(webPage));
    }

    configMapDR.reconcile(webPage, context);
    deploymentDR.reconcile(webPage, context);
    serviceDR.reconcile(webPage, context);

    if (Boolean.TRUE.equals(webPage.getSpec().getExposed())) {
      ingressDR.reconcile(webPage, context);
    } else {
      ingressDR.delete(webPage, context);
    }

    webPage.setStatus(
        createStatus(
            context.getSecondaryResource(ConfigMap.class).orElseThrow().getMetadata().getName()));
    return UpdateControl.patchStatus(webPage);
  }

  @Override
  public ErrorStatusUpdateControl<WebPage> updateErrorStatus(
      WebPage resource, Context<WebPage> retryInfo, Exception e) {
    return handleError(resource, e);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void configureDR(KubernetesDependentResource dr, KubernetesClient client) {
    dr.configureWith(config);
    dr.setKubernetesClient(client);
  }
}
