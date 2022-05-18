package io.javaoperatorsdk.operator.processing;

import org.slf4j.MDC;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class MDCUtils {

  private static final String NAME = "resource.name";
  private static final String NAMESPACE = "resource.namespace";
  private static final String API_VERSION = "resource.apiVersion";
  private static final String KIND = "resource.kind";
  private static final String RESOURCE_VERSION = "resource.resourceVersion";
  private static final String GENERATION = "resource.generation";
  private static final String UID = "resource.uid";

  public static void addResourceIDInfo(ResourceID resourceID) {
    MDC.put(NAME, resourceID.getName());
    MDC.put(NAMESPACE, resourceID.getNamespace().orElse("no namespace"));
  }

  public static void removeResourceIDInfo() {
    MDC.remove(NAME);
    MDC.remove(NAMESPACE);
  }

  public static void addResourceInfo(HasMetadata resource) {
    MDC.put(API_VERSION, resource.getApiVersion());
    MDC.put(KIND, resource.getKind());
    MDC.put(NAME, resource.getMetadata().getName());
    if (resource.getMetadata().getNamespace() != null) {
      MDC.put(NAMESPACE, resource.getMetadata().getNamespace());
    }
    MDC.put(RESOURCE_VERSION, resource.getMetadata().getResourceVersion());
    if (resource.getMetadata().getGeneration() != null) {
      MDC.put(GENERATION, resource.getMetadata().getGeneration().toString());
    }
    MDC.put(UID, resource.getMetadata().getUid());
  }

  public static void removeResourceInfo() {
    MDC.remove(API_VERSION);
    MDC.remove(KIND);
    MDC.remove(NAME);
    MDC.remove(NAMESPACE);
    MDC.remove(RESOURCE_VERSION);
    MDC.remove(GENERATION);
    MDC.remove(UID);
  }
}
