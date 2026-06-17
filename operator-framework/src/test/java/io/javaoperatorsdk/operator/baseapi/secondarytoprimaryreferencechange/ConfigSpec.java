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
package io.javaoperatorsdk.operator.baseapi.secondarytoprimaryreferencechange;

import java.util.List;

public class ConfigSpec {

  /**
   * Names of the {@link TargetCustomResource}s (in the same namespace) this config provides input
   * for. A single config can reference multiple targets.
   */
  private List<String> targetNames;

  /** Value to be applied to the referenced targets' status. */
  private String value;

  public List<String> getTargetNames() {
    return targetNames;
  }

  public ConfigSpec setTargetNames(List<String> targetNames) {
    this.targetNames = targetNames;
    return this;
  }

  public String getValue() {
    return value;
  }

  public ConfigSpec setValue(String value) {
    this.value = value;
    return this;
  }
}
