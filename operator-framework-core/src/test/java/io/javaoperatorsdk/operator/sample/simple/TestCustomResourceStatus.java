package io.javaoperatorsdk.operator.sample.simple;

import java.util.Objects;

public class TestCustomResourceStatus {

  private String configMapStatus;

  public String getConfigMapStatus() {
    return configMapStatus;
  }

  public void setConfigMapStatus(String configMapStatus) {
    this.configMapStatus = configMapStatus;
  }

  @Override
  public String toString() {
    return "TestCustomResourceStatus{" + "configMapStatus='" + configMapStatus + '\'' + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TestCustomResourceStatus that = (TestCustomResourceStatus) o;
    return Objects.equals(configMapStatus, that.configMapStatus);
  }

  @Override
  public int hashCode() {
    return Objects.hash(configMapStatus);
  }
}
