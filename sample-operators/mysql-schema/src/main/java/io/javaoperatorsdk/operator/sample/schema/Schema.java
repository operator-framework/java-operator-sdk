package io.javaoperatorsdk.operator.sample.schema;

import java.io.Serializable;
import java.util.Objects;

public class Schema implements Serializable {

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
    return Objects.equals(name, schema.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, characterSet);
  }

  @Override
  public String toString() {
    return "Schema{" + "name='" + name + '\'' + ", characterSet='" + characterSet + '\'' + '}';
  }
}
