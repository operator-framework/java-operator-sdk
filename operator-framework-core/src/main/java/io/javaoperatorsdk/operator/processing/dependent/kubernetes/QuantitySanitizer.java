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
import java.util.Objects;

import io.fabric8.kubernetes.api.model.Quantity;

/**
 * Replaces an actual {@link Quantity} with the desired one when both are numerically equal. The API
 * server stores quantities in canonical form (for example {@code 1000} as {@code 1k}), which would
 * otherwise cause a mismatch.
 */
class QuantitySanitizer {

  static void sanitizeQuantity(
      final Map<String, Object> parent,
      final String key,
      final Quantity actualQuantity,
      final Quantity desiredQuantity) {
    if (actualQuantity == null || desiredQuantity == null) {
      return;
    }
    // check if the string representation of the Quantity instances is equal
    if (Objects.equals(actualQuantity.getAmount(), desiredQuantity.getAmount())
        && Objects.equals(actualQuantity.getFormat(), desiredQuantity.getFormat())) {
      return;
    }
    // check if the numerical amount of the Quantity instances is equal
    if (actualQuantity.equals(desiredQuantity)) {
      // replace the actual Quantity with the desired Quantity to prevent a resource update
      parent.replace(key, desiredQuantity.toString());
    }
  }

  static void sanitizeQuantities(
      final Map<String, Object> parent,
      final Map<String, Quantity> actualQuantities,
      final Map<String, Quantity> desiredQuantities) {
    if (actualQuantities == null || desiredQuantities == null) {
      return;
    }
    actualQuantities.forEach(
        (key, actualQuantity) ->
            sanitizeQuantity(parent, key, actualQuantity, desiredQuantities.get(key)));
  }
}
