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
package io.javaoperatorsdk.operator.dependent.bulkdependent;

public class BulkDependentTestSpec {

  private Integer numberOfResources;
  private String additionalData;

  public Integer getNumberOfResources() {
    return numberOfResources;
  }

  public BulkDependentTestSpec setNumberOfResources(Integer numberOfResources) {
    this.numberOfResources = numberOfResources;
    return this;
  }

  public BulkDependentTestSpec setAdditionalData(String additionalData) {
    this.additionalData = additionalData;
    return this;
  }

  public String getAdditionalData() {
    return additionalData;
  }
}
