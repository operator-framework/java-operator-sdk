package io.javaoperatorsdk.operator.sample.generickubernetesresource.generickubernetesresourcehandling;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration
public class GenericKubernetesResourceHandlingReconciler
    implements Reconciler<GenericKubernetesResourceHandlingCustomResource>,
    EventSourceInitializer<GenericKubernetesResourceHandlingCustomResource> {


  public static final String VERSION = "v1";
  public static final String KIND = "ConfigMap";
  public static final String KEY = "key";

  @Override
  public UpdateControl<GenericKubernetesResourceHandlingCustomResource> reconcile(
      GenericKubernetesResourceHandlingCustomResource primary,
      Context<GenericKubernetesResourceHandlingCustomResource> context) {

    var secondary = context.getSecondaryResource(GenericKubernetesResource.class);

    secondary.ifPresentOrElse(r -> {
      var desired = desiredConfigMap(primary, context);
      if (!matches(r, desired)) {
        context.getClient().genericKubernetesResources(VERSION, KIND).resource(desired).update();
      }
    }, () -> context.getClient().genericKubernetesResources(VERSION, KIND)
        .resource(desiredConfigMap(primary, context)).create());

    return UpdateControl.noUpdate();
  }

  @SuppressWarnings("unchecked")
  private boolean matches(GenericKubernetesResource actual, GenericKubernetesResource desired) {
    var actualData = (HashMap<String, String>) actual.getAdditionalProperties().get("data");
    var desiredData = (HashMap<String, String>) desired.getAdditionalProperties().get("data");
    return actualData.equals(desiredData);
  }

  GenericKubernetesResource desiredConfigMap(
      GenericKubernetesResourceHandlingCustomResource primary,
      Context<GenericKubernetesResourceHandlingCustomResource> context) {
    try (InputStream is = this.getClass().getResourceAsStream("/configmap.yaml")) {
      var res = context.getClient().genericKubernetesResources(VERSION, KIND).load(is).item();
      res.getMetadata().setName(primary.getMetadata().getName());
      res.getMetadata().setNamespace(primary.getMetadata().getNamespace());
      Map<String, String> data = (Map<String, String>) res.getAdditionalProperties().get("data");
      data.put(KEY, primary.getSpec().getValue());
      res.addOwnerReference(primary);
      return res;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }


  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<GenericKubernetesResourceHandlingCustomResource> context) {

    var informerEventSource = new InformerEventSource<>(InformerConfiguration.from(
        new GroupVersionKind("", VERSION, KIND), context).build(),
        context);

    return EventSourceInitializer.nameEventSources(informerEventSource);
  }
}
