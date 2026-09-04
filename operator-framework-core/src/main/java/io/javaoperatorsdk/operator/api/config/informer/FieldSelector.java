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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class FieldSelector {
  private final List<Field> fields;

  public FieldSelector(List<Field> fields) {
    this.fields = fields;
  }

  public FieldSelector(Field... fields) {
    this.fields = Arrays.asList(fields);
  }

  public List<Field> getFields() {
    return fields;
  }

  public record Field(String path, String value, boolean negated) {
    public Field(String path, String value) {
      this(path, value, false);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    FieldSelector that = (FieldSelector) o;
    return Objects.equals(fields, that.fields);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(fields);
  }

  @Override
  public String toString() {
    return "FieldSelector{" + "fields=" + fields + '}';
  }
}
