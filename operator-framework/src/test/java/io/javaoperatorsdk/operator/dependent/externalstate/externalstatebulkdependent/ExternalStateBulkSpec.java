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
