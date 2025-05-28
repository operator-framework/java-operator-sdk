package io.javaoperatorsdk.operator.processing.event.source;

import java.io.Serializable;
import java.util.Objects;

import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class SampleExternalResource implements Serializable {

  public static final String DEFAULT_VALUE_1 = "value1";
  public static final String DEFAULT_VALUE_2 = "value2";
  public static final String NAME_1 = "name1";
  public static final String NAME_2 = "name2";

  public static SampleExternalResource testResource1() {
    return new SampleExternalResource(NAME_1, DEFAULT_VALUE_1);
  }

  public static SampleExternalResource testResource2() {
    return new SampleExternalResource(NAME_2, DEFAULT_VALUE_2);
  }

  public static ResourceID primaryID1() {
    return new ResourceID(NAME_1, "testns");
  }

  public static ResourceID primaryID2() {
    return new ResourceID(NAME_2, "testns");
  }

  private String name;
  private String value;

  public SampleExternalResource(String name, String value) {
    this.name = name;
    this.value = value;
  }

  public String getName() {
    return name;
  }

  public SampleExternalResource setName(String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public SampleExternalResource setValue(String value) {
    this.value = value;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SampleExternalResource that = (SampleExternalResource) o;
    return Objects.equals(name, that.name) && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, value);
  }
}
