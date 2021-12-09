package io.javaoperatorsdk.operator.sample;

import io.javaoperatorsdk.operator.api.ObservedGenerationAwareStatus;

public class SchemaStatus extends ObservedGenerationAwareStatus {

  private String url;

  private String status;

  private String userName;

  private String secretName;

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getUserName() {
    return userName;
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }

  public String getSecretName() {
    return secretName;
  }

  public void setSecretName(String secretName) {
    this.secretName = secretName;
  }
}
