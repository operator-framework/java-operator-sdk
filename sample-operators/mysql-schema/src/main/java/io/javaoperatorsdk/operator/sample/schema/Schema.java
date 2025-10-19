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
package io.javaoperatorsdk.operator.sample.schema;

import java.io.Serializable;
import java.util.Objects;

import io.javaoperatorsdk.operator.processing.ResourceIDProvider;

public class Schema implements Serializable, ResourceIDProvider<String> {

  private final String name;
  private final String characterSet;

  public Schema(String name, String characterSet) {
    this.name = name;
    this.characterSet = characterSet;
  }

  public String getName() {
    return name;
  }

  public String getCharacterSet() {
    return characterSet;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Schema schema = (Schema) o;
    return Objects.equals(name, schema.name) && Objects.equals(characterSet, schema.characterSet);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

  @Override
  public String toString() {
    return "Schema{" + "name='" + name + '\'' + ", characterSet='" + characterSet + '\'' + '}';
  }

  @Override
  public String resourceId() {
    return name;
  }
}
