package io.javaoperatorsdk.operator.sample;

import io.javaoperatorsdk.operator.api.ObservedGenerationAwareStatus;

public class WebPageStatus extends ObservedGenerationAwareStatus {

  private String htmlConfigMap;

  private String areWeGood;

  private String errorMessage;

  public String getHtmlConfigMap() {
    return htmlConfigMap;
  }

  public void setHtmlConfigMap(String htmlConfigMap) {
    this.htmlConfigMap = htmlConfigMap;
  }

  public String getAreWeGood() {
    return areWeGood;
  }

  public void setAreWeGood(String areWeGood) {
    this.areWeGood = areWeGood;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public WebPageStatus setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
    return this;
  }
}
