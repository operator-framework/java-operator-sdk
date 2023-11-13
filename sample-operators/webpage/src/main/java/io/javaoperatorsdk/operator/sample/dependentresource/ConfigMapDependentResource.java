package io.javaoperatorsdk.operator.sample.dependentresource;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.sample.customresource.WebPage;

import static io.javaoperatorsdk.operator.sample.Utils.configMapName;
import static io.javaoperatorsdk.operator.sample.WebPageManagedDependentsReconciler.SELECTOR;

// this annotation only activates when using managed dependents and is not otherwise needed
@KubernetesDependent(labelSelector = SELECTOR)
public class ConfigMapDependentResource
    extends CRUDKubernetesDependentResource<ConfigMap, WebPage> {

  private static final Logger log = LoggerFactory.getLogger(ConfigMapDependentResource.class);

  public ConfigMapDependentResource() {
    super(ConfigMap.class);
  }

  @Override
  protected ConfigMap desired(WebPage webPage, Context<WebPage> context) {
    log.debug("Web page spec: {}", webPage.getSpec().getHtml());
    Map<String, String> data = new HashMap<>();
    data.put("index.html", webPage.getSpec().getHtml());
    Map<String, String> labels = new HashMap<>();
    labels.put(SELECTOR, "true");
    return new ConfigMapBuilder()
        .withMetadata(
            new ObjectMetaBuilder()
                .withName(configMapName(webPage))
                .withNamespace(webPage.getMetadata().getNamespace())
                .withLabels(labels)
                .build())
        .withData(data)
        .build();
  }

  @Override
  public Result<ConfigMap> match(ConfigMap actualResource, WebPage primary,
      Context<WebPage> context) {
    var matched = super.match(actualResource, primary, context);
    log.debug("Match for config map {} res: {}", actualResource.getMetadata().getName(),
        matched.matched());
    return matched;
  }


}
