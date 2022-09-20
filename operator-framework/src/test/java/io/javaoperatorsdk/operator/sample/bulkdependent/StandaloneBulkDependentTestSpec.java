package io.javaoperatorsdk.operator.sample.bulkdependent;

public class StandaloneBulkDependentTestSpec {

  private Integer numberOfResources;

  public Integer getNumberOfResources() {
    return numberOfResources;
  }

  public StandaloneBulkDependentTestSpec setNumberOfResources(Integer numberOfResources) {
    this.numberOfResources = numberOfResources;
    return this;
  }
}
