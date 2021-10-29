package io.javaoperatorsdk.operator.processing;

import org.slf4j.MDC;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.processing.event.CustomResourceID;

public class MDCUtils {

  private static final String NAME = "resource.name";
  private static final String NAMESPACE = "resource.namespace";
  private static final String API_VERSION = "resource.apiVersion";
  private static final String KIND = "resource.kind";
  private static final String RESOURCE_VERSION = "resource.resourceVersion";
  private static final String GENERATION = "resource.generation";
  private static final String UID = "resource.uid";

  public static void addCustomResourceIDInfo(CustomResourceID customResourceID) {
    MDC.put(NAME, customResourceID.getName());
    MDC.put(NAMESPACE, customResourceID.getNamespace().orElse("no namespace"));
  }

  public static void removeCustomResourceIDInfo() {
    MDC.remove(NAME);
    MDC.remove(NAMESPACE);
  }

  public static void addCustomResourceInfo(CustomResource<?, ?> customResource) {
    MDC.put(API_VERSION, customResource.getApiVersion());
    MDC.put(KIND, customResource.getKind());
    MDC.put(NAME, customResource.getMetadata().getName());
    MDC.put(NAMESPACE, customResource.getMetadata().getNamespace());
    MDC.put(RESOURCE_VERSION, customResource.getMetadata().getResourceVersion());
    MDC.put(GENERATION, customResource.getMetadata().getGeneration().toString());
    MDC.put(UID, customResource.getMetadata().getUid());
  }

  public static void removeCustomResourceInfo() {
    MDC.remove(API_VERSION);
    MDC.remove(KIND);
    MDC.remove(NAME);
    MDC.remove(NAMESPACE);
    MDC.remove(RESOURCE_VERSION);
    MDC.remove(GENERATION);
    MDC.remove(UID);
  }
}
