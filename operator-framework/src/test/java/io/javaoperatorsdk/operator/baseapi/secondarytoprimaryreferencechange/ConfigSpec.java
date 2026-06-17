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

public class ConfigSpec {

  /**
   * Name of the {@link TargetCustomResource} (in the same namespace) this config provides input.
   */
  private String targetName;

  /** Value to be applied to the referenced target's status. */
  private String value;

  public String getTargetName() {
    return targetName;
  }

  public ConfigSpec setTargetName(String targetName) {
    this.targetName = targetName;
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
