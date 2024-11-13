package io.javaoperatorsdk.operator.sample.generickubernetesresource.generickubernetesdependentresourcemanaged;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent
public class ConfigMapGenericKubernetesDependent extends
    GenericKubernetesDependentResource<GenericKubernetesDependentManagedCustomResource>
    implements
    Creator<GenericKubernetesResource, GenericKubernetesDependentManagedCustomResource>,
    Updater<GenericKubernetesResource, GenericKubernetesDependentManagedCustomResource>,
    GarbageCollected<GenericKubernetesDependentManagedCustomResource> {

  public static final String VERSION = "v1";
  public static final String KIND = "ConfigMap";
  public static final String KEY = "key";

  public ConfigMapGenericKubernetesDependent() {
    super(new GroupVersionKind("", VERSION, KIND));
  }

  @Override
  protected GenericKubernetesResource desired(
      GenericKubernetesDependentManagedCustomResource primary,
      Context<GenericKubernetesDependentManagedCustomResource> context) {

    try (InputStream is = this.getClass().getResourceAsStream("/configmap.yaml")) {
      var res = context.getClient().genericKubernetesResources(VERSION, KIND).load(is).item();
      res.getMetadata().setName(primary.getMetadata().getName());
      res.getMetadata().setNamespace(primary.getMetadata().getNamespace());
      Map<String, String> data = (Map<String, String>) res.getAdditionalProperties().get("data");
      data.put(KEY, primary.getSpec().getValue());
      return res;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
