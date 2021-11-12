package io.javaoperatorsdk.operator.sample;

public class WebappStatus {

  private String deployedArtifact;

  public String getDeployedArtifact() {
    return deployedArtifact;
  }

  public void setDeployedArtifact(String deployedArtifact) {
    this.deployedArtifact = deployedArtifact;
  }

  private String[] deploymentStatus;

  public String[] getDeploymentStatus() {
    return deploymentStatus;
  }

  public void setDeploymentStatus(String[] deploymentStatus) {
    this.deploymentStatus = deploymentStatus;
  }
}
