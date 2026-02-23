/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.processing;

import org.slf4j.MDC;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ResourceAction;

public class MDCUtils {

  private static final String NAME = "resource.name";
  private static final String NAMESPACE = "resource.namespace";
  private static final String API_VERSION = "resource.apiVersion";
  private static final String KIND = "resource.kind";
  private static final String RESOURCE_VERSION = "resource.resourceVersion";
  private static final String GENERATION = "resource.generation";
  private static final String UID = "resource.uid";
  private static final String NO_NAMESPACE = "no namespace";
  private static final boolean enabled =
      Utils.getBooleanFromSystemPropsOrDefault(Utils.USE_MDC_ENV_KEY, true);

  private static final String EVENT_RESOURCE_NAME = "eventsource.event.resource.name";
  private static final String EVENT_RESOURCE_UID = "eventsource.event.resource.uid";
  private static final String EVENT_RESOURCE_NAMESPACE = "eventsource.event.resource.namespace";
  private static final String EVENT_RESOURCE_KIND = "eventsource.event.resource.kind";
  private static final String EVENT_RESOURCE_VERSION = "eventsource.event.resource.resourceVersion";
  private static final String EVENT_ACTION = "eventsource.event.action";
  private static final String EVENT_SOURCE_NAME = "eventsource.name";

  public static void addInformerEventInfo(
      HasMetadata resource, ResourceAction action, String eventSourceName) {
    if (enabled) {
      MDC.put(EVENT_RESOURCE_NAME, resource.getMetadata().getName());
      MDC.put(EVENT_RESOURCE_NAMESPACE, resource.getMetadata().getNamespace());
      MDC.put(EVENT_RESOURCE_KIND, HasMetadata.getKind(resource.getClass()));
      MDC.put(EVENT_RESOURCE_VERSION, resource.getMetadata().getResourceVersion());
      MDC.put(EVENT_RESOURCE_UID, resource.getMetadata().getUid());
      MDC.put(EVENT_ACTION, action == null ? null : action.name());
      MDC.put(EVENT_SOURCE_NAME, eventSourceName);
    }
  }

  public static void removeInformerEventInfo() {
    if (enabled) {
      MDC.remove(EVENT_RESOURCE_NAME);
      MDC.remove(EVENT_RESOURCE_NAMESPACE);
      MDC.remove(EVENT_RESOURCE_KIND);
      MDC.remove(EVENT_RESOURCE_VERSION);
      MDC.remove(EVENT_RESOURCE_UID);
      MDC.remove(EVENT_ACTION);
      MDC.remove(EVENT_SOURCE_NAME);
    }
  }

  public static void withMDCForEvent(
      HasMetadata resource, Runnable runnable, String eventSourceName) {
    withMDCForEvent(resource, null, runnable, eventSourceName);
  }

  public static void withMDCForEvent(
      HasMetadata resource, ResourceAction action, Runnable runnable, String eventSourceName) {
    try {
      MDCUtils.addInformerEventInfo(resource, action, eventSourceName);
      runnable.run();
    } finally {
      MDCUtils.removeInformerEventInfo();
    }
  }

  public static void withMDCForResourceID(ResourceID resourceID, Runnable runnable) {
    try {
      MDCUtils.addResourceIDInfo(resourceID);
      runnable.run();
    } finally {
      MDCUtils.removeResourceIDInfo();
    }
  }

  public static void withMDCForPrimary(HasMetadata primary, Runnable runnable) {
    try {
      MDCUtils.addResourceInfo(primary);
      runnable.run();
    } finally {
      MDCUtils.removeResourceInfo();
    }
  }

  public static void addResourceIDInfo(ResourceID resourceID) {
    if (enabled) {
      MDC.put(NAME, resourceID.getName());
      MDC.put(NAMESPACE, resourceID.getNamespace().orElse(NO_NAMESPACE));
    }
  }

  public static void removeResourceIDInfo() {
    if (enabled) {
      MDC.remove(NAME);
      MDC.remove(NAMESPACE);
    }
  }

  public static void addResourceInfo(HasMetadata resource) {
    if (enabled) {
      MDC.put(API_VERSION, resource.getApiVersion());
      MDC.put(KIND, resource.getKind());
      final var metadata = resource.getMetadata();
      if (metadata != null) {
        MDC.put(NAME, metadata.getName());
        if (metadata.getNamespace() != null) {
          MDC.put(NAMESPACE, metadata.getNamespace());
        }
        MDC.put(RESOURCE_VERSION, metadata.getResourceVersion());
        if (metadata.getGeneration() != null) {
          MDC.put(GENERATION, metadata.getGeneration().toString());
        }
        MDC.put(UID, metadata.getUid());
      }
    }
  }

  public static void removeResourceInfo() {
    if (enabled) {
      MDC.remove(API_VERSION);
      MDC.remove(KIND);
      MDC.remove(NAME);
      MDC.remove(NAMESPACE);
      MDC.remove(RESOURCE_VERSION);
      MDC.remove(GENERATION);
      MDC.remove(UID);
    }
  }
}
