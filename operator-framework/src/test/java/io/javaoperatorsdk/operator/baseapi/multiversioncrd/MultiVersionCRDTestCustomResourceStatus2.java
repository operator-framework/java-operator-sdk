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
package io.javaoperatorsdk.operator.baseapi.multiversioncrd;

import java.util.ArrayList;
import java.util.List;

public class MultiVersionCRDTestCustomResourceStatus2 {

  private int value1;

  private List<String> reconciledBy = new ArrayList<>();

  public int getValue1() {
    return value1;
  }

  public MultiVersionCRDTestCustomResourceStatus2 setValue1(int value1) {
    this.value1 = value1;
    return this;
  }

  public List<String> getReconciledBy() {
    return reconciledBy;
  }

  public MultiVersionCRDTestCustomResourceStatus2 setReconciledBy(List<String> reconciledBy) {
    this.reconciledBy = reconciledBy;
    return this;
  }
}
