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
package io.javaoperatorsdk.operator.processing.expectation;

import java.util.function.BiPredicate;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Experimental;

@Experimental("based on feedback the API might change")
public interface Expectation<P extends HasMetadata> {

  String UNNAMED = "unnamed";

  boolean isFulfilled(P primary, Context<P> context);

  default String name() {
    return UNNAMED;
  }

  static <P extends HasMetadata> Expectation<P> createExpectation(
      String name, BiPredicate<P, Context<P>> predicate) {
    return new Expectation<>() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public boolean isFulfilled(P primary, Context<P> context) {
        return predicate.test(primary, context);
      }
    };
  }
}
