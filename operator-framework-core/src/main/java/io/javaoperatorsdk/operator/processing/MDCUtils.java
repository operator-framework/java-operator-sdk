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

  private static final String EVENT_SOURCE_PREFIX = "eventsource.event.";
  private static final String EVENT_ACTION = EVENT_SOURCE_PREFIX + "action";
  private static final String EVENT_SOURCE_NAME = "eventsource.name";
  private static final String UNKNOWN_ACTION = "unknown action";

  public static void addInformerEventInfo(
      HasMetadata resource, ResourceAction action, String eventSourceName) {
    if (enabled) {
      addResourceInfo(resource, true);
      MDC.put(EVENT_ACTION, action == null ? UNKNOWN_ACTION : action.name());
      MDC.put(EVENT_SOURCE_NAME, eventSourceName);
    }
  }

  public static void removeInformerEventInfo() {
    if (enabled) {
      removeResourceInfo(true);
      MDC.remove(EVENT_ACTION);
      MDC.remove(EVENT_SOURCE_NAME);
    }
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
    addResourceInfo(resource, false);
  }

  public static void addResourceInfo(HasMetadata resource, boolean forEventSource) {
    if (enabled) {
      MDC.put(key(API_VERSION, forEventSource), resource.getApiVersion());
      MDC.put(key(KIND, forEventSource), resource.getKind());
      final var metadata = resource.getMetadata();
      if (metadata != null) {
        MDC.put(key(NAME, forEventSource), metadata.getName());
        if (metadata.getNamespace() != null) {
          MDC.put(key(NAMESPACE, forEventSource), metadata.getNamespace());
        }
        MDC.put(key(RESOURCE_VERSION, forEventSource), metadata.getResourceVersion());
        if (metadata.getGeneration() != null) {
          MDC.put(key(GENERATION, forEventSource), metadata.getGeneration().toString());
        }
        MDC.put(key(UID, forEventSource), metadata.getUid());
      }
    }
  }

  private static String key(String baseKey, boolean forEventSource) {
    return forEventSource ? EVENT_SOURCE_PREFIX + baseKey : baseKey;
  }

  public static void removeResourceInfo() {
    removeResourceInfo(false);
  }

  public static void removeResourceInfo(boolean forEventSource) {
    if (enabled) {
      MDC.remove(key(API_VERSION, forEventSource));
      MDC.remove(key(KIND, forEventSource));
      MDC.remove(key(NAME, forEventSource));
      MDC.remove(key(NAMESPACE, forEventSource));
      MDC.remove(key(RESOURCE_VERSION, forEventSource));
      MDC.remove(key(GENERATION, forEventSource));
      MDC.remove(key(UID, forEventSource));
    }
  }
}
