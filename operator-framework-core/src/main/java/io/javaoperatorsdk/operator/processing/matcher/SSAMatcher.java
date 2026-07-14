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
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.matcher.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.SSABasedGenericKubernetesResourceMatcher;

public class SSAMatcher implements Matcher {

  private static final SSAMatcher INSTANCE = new SSAMatcher();

  public static SSAMatcher getInstance() {
    return INSTANCE;
  }

  private SSAMatcher() {}

  @Override
  public boolean matches(HasMetadata desired, HasMetadata actual, Context<?> context) {
    return SSABasedGenericKubernetesResourceMatcher.getInstance().matches(actual, desired, context);
  }
}
