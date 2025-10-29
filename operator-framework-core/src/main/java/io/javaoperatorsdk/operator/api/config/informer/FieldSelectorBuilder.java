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
package io.javaoperatorsdk.operator.api.config.informer;

import java.util.ArrayList;
import java.util.List;

public class FieldSelectorBuilder {

  private final List<FieldSelector.Field> fields = new ArrayList<>();

  public FieldSelectorBuilder withField(String path, String value) {
    fields.add(new FieldSelector.Field(path, value));
    return this;
  }

  public FieldSelectorBuilder withoutField(String path, String value) {
    fields.add(new FieldSelector.Field(path, value, true));
    return this;
  }

  public FieldSelector build() {
    return new FieldSelector(fields);
  }
}
