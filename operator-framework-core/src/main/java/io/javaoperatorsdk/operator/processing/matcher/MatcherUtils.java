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
package io.javaoperatorsdk.operator.processing.matcher;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.zjsonpatch.JsonDiff;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.matcher.Matcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Shared helpers for the client-side patch based {@link Matcher} implementations. */
final class MatcherUtils {

  private static final String STATUS = "status";

  private MatcherUtils() {}

  static JsonNode toNode(HasMetadata resource, Context<?> context) {
    return context.getClient().getKubernetesSerialization().convertValue(resource, JsonNode.class);
  }

  /** The {@code status} subresource as a node, or a {@link NullNode} if it is not present. */
  static JsonNode statusNode(HasMetadata resource, Context<?> context) {
    var status = toNode(resource, context).get(STATUS);
    return status == null ? NullNode.getInstance() : status;
  }

  /**
   * @return {@code true} if applying the desired state as a JSON Patch (RFC 6902) to the actual
   *     state would be a no-op, i.e. the computed patch contains no operations.
   */
  static boolean jsonPatchMatches(JsonNode actual, JsonNode desired) {
    return JsonDiff.asJson(actual, desired).isEmpty();
  }

  /**
   * @return {@code true} if applying the desired state as a JSON Merge Patch (RFC 7386) to the
   *     actual state would be a no-op, i.e. every value present in the desired state already equals
   *     the actual state (additional values only present in the actual state are allowed).
   */
  static boolean mergePatchMatches(JsonNode actual, JsonNode desired) {
    return applyMergePatch(actual.deepCopy(), desired).equals(actual);
  }

  // See https://datatracker.ietf.org/doc/html/rfc7386#section-2
  private static JsonNode applyMergePatch(JsonNode target, JsonNode patch) {
    if (!patch.isObject()) {
      return patch;
    }
    var targetObject =
        target.isObject() ? (ObjectNode) target : JsonNodeFactory.instance.objectNode();
    var fields = patch.fields();
    while (fields.hasNext()) {
      var entry = fields.next();
      if (entry.getValue().isNull()) {
        targetObject.remove(entry.getKey());
      } else {
        var current = targetObject.get(entry.getKey());
        targetObject.set(
            entry.getKey(),
            applyMergePatch(current == null ? NullNode.getInstance() : current, entry.getValue()));
      }
    }
    return targetObject;
  }
}
