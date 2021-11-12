package io.javaoperatorsdk.operator.processing;

import org.slf4j.MDC;

import io.fabric8.kubernetes.api.model.HasMetadata;
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

  public static void addCustomResourceInfo(HasMetadata resource) {
    MDC.put(API_VERSION, resource.getApiVersion());
    MDC.put(KIND, resource.getKind());
    MDC.put(NAME, resource.getMetadata().getName());
    MDC.put(NAMESPACE, resource.getMetadata().getNamespace());
    MDC.put(RESOURCE_VERSION, resource.getMetadata().getResourceVersion());
    if (resource.getMetadata().getGeneration() != null) {
      MDC.put(GENERATION, resource.getMetadata().getGeneration().toString());
    }
    MDC.put(UID, resource.getMetadata().getUid());
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
