package io.javaoperatorsdk.operator.sample.bulkdependent;

public class BulkDependentTestSpec {

  private Integer numberOfResources;

  public Integer getNumberOfResources() {
    return numberOfResources;
  }

  public BulkDependentTestSpec setNumberOfResources(Integer numberOfResources) {
    this.numberOfResources = numberOfResources;
    return this;
  }
}
