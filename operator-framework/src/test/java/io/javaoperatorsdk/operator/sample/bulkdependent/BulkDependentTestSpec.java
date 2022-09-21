package io.javaoperatorsdk.operator.sample.bulkdependent;

public class BulkDependentTestSpec {

  private Integer numberOfResources;
  private String additionalData;

  public Integer getNumberOfResources() {
    return numberOfResources;
  }

  public BulkDependentTestSpec setNumberOfResources(Integer numberOfResources) {
    this.numberOfResources = numberOfResources;
    return this;
  }

  public BulkDependentTestSpec setAdditionalData(String additionalData) {
    this.additionalData = additionalData;
    return this;
  }

  public String getAdditionalData() {
    return additionalData;
  }
}
