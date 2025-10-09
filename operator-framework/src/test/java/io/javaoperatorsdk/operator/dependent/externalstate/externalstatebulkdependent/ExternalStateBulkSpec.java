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
package io.javaoperatorsdk.operator.dependent.externalstate.externalstatebulkdependent;

public class ExternalStateBulkSpec {

  private Integer number;
  private String data;

  public String getData() {
    return data;
  }

  public ExternalStateBulkSpec setData(String data) {
    this.data = data;
    return this;
  }

  public Integer getNumber() {
    return number;
  }

  public ExternalStateBulkSpec setNumber(Integer number) {
    this.number = number;
    return this;
  }
}
