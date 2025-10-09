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
package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Map;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public class GenericResourceUpdater {

  private static final String METADATA = "metadata";

  @SuppressWarnings("unchecked")
  public static <R extends HasMetadata> R updateResource(R actual, R desired, Context<?> context) {
    KubernetesSerialization kubernetesSerialization =
        context.getClient().getKubernetesSerialization();
    Map<String, Object> actualMap = kubernetesSerialization.convertValue(actual, Map.class);
    Map<String, Object> desiredMap = kubernetesSerialization.convertValue(desired, Map.class);
    // replace all top level fields from actual with desired, but merge metadata separately
    // note that this ensures that `resourceVersion` is present, therefore optimistic
    // locking will happen on server side
    var metadata = actualMap.remove(METADATA);
    actualMap.replaceAll((k, v) -> desiredMap.get(k));
    actualMap.putAll(desiredMap);
    actualMap.put(METADATA, metadata);
    var clonedActual = (R) kubernetesSerialization.convertValue(actualMap, desired.getClass());
    updateLabelsAndAnnotation(clonedActual, desired);
    return clonedActual;
  }

  public static <K extends HasMetadata> void updateLabelsAndAnnotation(K actual, K desired) {
    actual.getMetadata().getLabels().putAll(desired.getMetadata().getLabels());
    actual.getMetadata().getAnnotations().putAll(desired.getMetadata().getAnnotations());
  }
}
