package io.javaoperatorsdk.operator.sample;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryToSecondaryMapper;

import static io.javaoperatorsdk.operator.sample.Utils.configMapName;
import static io.javaoperatorsdk.operator.sample.Utils.deploymentName;

// this annotation only activates when using managed dependents and is not otherwise needed
@KubernetesDependent(labelSelector = WebPageManagedDependentsReconciler.SELECTOR)
class ConfigMapDependentResource extends CRUKubernetesDependentResource<ConfigMap, WebPage>
    implements PrimaryToSecondaryMapper<WebPage> {

  private static final Logger log = LoggerFactory.getLogger(ConfigMapDependentResource.class);

  public ConfigMapDependentResource() {
    super(ConfigMap.class);
  }

  @Override
  protected ConfigMap desired(WebPage webPage, Context<WebPage> context) {
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
  }

  @Override
  public ConfigMap update(ConfigMap actual, ConfigMap target, WebPage primary,
      Context<WebPage> context) {
    var res = super.update(actual, target, primary, context);
    var ns = actual.getMetadata().getNamespace();
    log.info("Restarting pods because HTML has changed in {}",
        ns);
    getKubernetesClient()
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
