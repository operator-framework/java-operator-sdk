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

import io.javaoperatorsdk.operator.api.reconciler.matcher.Matcher;

public enum UpdateType {
  UPDATE(UpdateMatcher.getInstance()),
  UPDATE_STATUS(UpdateStatusMatcher.getInstance()),
  SSA(SSAMatcher.getInstance()),
  SSA_STATUS(SSAStatusMatcher.getInstance()),
  JSON_PATCH(JsonPatchMatcher.getInstance()),
  JSON_PATCH_STATUS(JsonPatchStatusMatcher.getInstance()),
  JSON_MERGE_PATCH(JsonMergePatchMatcher.getInstance()),
  JSON_MERGE_PATCH_STATUS(JsonMergePatchStatusMatcher.getInstance());

  private final Matcher defaultMatcher;

  UpdateType(Matcher defaultMatcher) {
    this.defaultMatcher = defaultMatcher;
  }

  public Matcher getMatcher() {
    return defaultMatcher;
  }
}
