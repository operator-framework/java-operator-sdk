package io.javaoperatorsdk.operator.sample.bulkdependent;

public class DynamicDependentTestSpec {

  private Integer numberOfResources;
  private String additionalData;

  public Integer getNumberOfResources() {
    return numberOfResources;
  }

  public DynamicDependentTestSpec setNumberOfResources(Integer numberOfResources) {
    this.numberOfResources = numberOfResources;
    return this;
  }

  public DynamicDependentTestSpec setAdditionalData(String additionalData) {
    this.additionalData = additionalData;
    return this;
  }

  public String getAdditionalData() {
    return additionalData;
  }
}
