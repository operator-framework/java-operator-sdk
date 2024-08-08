package io.javaoperatorsdk.operator.dependent.generickubernetesresource.generickubernetesdependentstandalone;

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

public class ConfigMapGenericKubernetesDependent extends
    GenericKubernetesDependentResource<GenericKubernetesDependentStandaloneCustomResource>
    implements
    Creator<GenericKubernetesResource, GenericKubernetesDependentStandaloneCustomResource>,
    Updater<GenericKubernetesResource, GenericKubernetesDependentStandaloneCustomResource>,
    GarbageCollected<GenericKubernetesDependentStandaloneCustomResource> {

  public static final String VERSION = "v1";
  public static final String KIND = "ConfigMap";
  public static final String KEY = "key";

  public ConfigMapGenericKubernetesDependent() {
    super(new GroupVersionKind("", VERSION, KIND));
  }

  @Override
  protected GenericKubernetesResource desired(
      GenericKubernetesDependentStandaloneCustomResource primary,
      Context<GenericKubernetesDependentStandaloneCustomResource> context) {

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
