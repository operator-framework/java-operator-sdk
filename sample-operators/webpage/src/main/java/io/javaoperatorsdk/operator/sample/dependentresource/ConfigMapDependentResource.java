package io.javaoperatorsdk.operator.sample.dependentresource;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.sample.customresource.WebPage;

import static io.javaoperatorsdk.operator.sample.Utils.configMapName;
import static io.javaoperatorsdk.operator.sample.Utils.deploymentName;
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
    log.info("custom value: {}", ((MyConfig) configuration().orElseThrow()).getCustomValue());
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
  public ConfigMap update(ConfigMap actual, ConfigMap target, WebPage primary,
      Context<WebPage> context) {
    var res = super.update(actual, target, primary, context);
    var ns = actual.getMetadata().getNamespace();
    log.info("Restarting pods because HTML has changed in {}",
        ns);
    // not that this is not necessary, eventually mounted config map would be updated, just this way
    // is much faster; what is handy for demo purposes.
    // https://kubernetes.io/docs/tasks/configure-pod-container/configure-pod-configmap/#mounted-configmaps-are-updated-automatically
    getKubernetesClient()
        .pods()
        .inNamespace(ns)
        .withLabel("app", deploymentName(primary))
        .delete();
    return res;
  }

  public static class MyConfig extends KubernetesDependentResourceConfig<ConfigMap> {

    public MyConfig(String customValue) {
      this.customValue = customValue;
    }

    public MyConfig() {}

    public MyConfig(Set<String> namespaces, String labelSelector,
        boolean configuredNS, ResourceDiscriminator<ConfigMap, ?> resourceDiscriminator,
        OnAddFilter<ConfigMap> onAddFilter, OnUpdateFilter<ConfigMap> onUpdateFilter,
        OnDeleteFilter<ConfigMap> onDeleteFilter, GenericFilter<ConfigMap> genericFilter,
        String customValue) {
      super(namespaces, labelSelector, configuredNS, resourceDiscriminator, onAddFilter,
          onUpdateFilter, onDeleteFilter, genericFilter);
      this.customValue = customValue;
    }

    private String customValue;

    public String getCustomValue() {
      return customValue;
    }

    public void setCustomValue(String customValue) {
      this.customValue = customValue;
    }
  }

}
