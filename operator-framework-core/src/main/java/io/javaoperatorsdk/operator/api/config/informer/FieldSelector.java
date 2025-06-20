package io.javaoperatorsdk.operator.api.config.informer;

import java.util.Arrays;
import java.util.List;

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
    public Field(String value, String path) {
      this(path, value, false);
    }
  }
}
