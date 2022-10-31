package io.javaoperatorsdk.operator.sample.complexdependent;

public class ComplexDependentSpec {
  private String projectId;

  public String getProjectId() {
    return projectId;
  }

  public ComplexDependentSpec setProjectId(String projectId) {
    this.projectId = projectId;
    return this;
  }
}
