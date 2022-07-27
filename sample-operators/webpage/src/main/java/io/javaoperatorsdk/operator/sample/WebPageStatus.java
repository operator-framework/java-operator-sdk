package io.javaoperatorsdk.operator.sample;

import java.util.Objects;

import io.javaoperatorsdk.operator.api.ObservedGenerationAwareStatus;

public class WebPageStatus extends ObservedGenerationAwareStatus {

  private String htmlConfigMap;

  private Boolean areWeGood;

  private String errorMessage;

  public String getHtmlConfigMap() {
    return htmlConfigMap;
  }

  public void setHtmlConfigMap(String htmlConfigMap) {
    this.htmlConfigMap = htmlConfigMap;
  }

  public Boolean getAreWeGood() {
    return areWeGood;
  }

  public void setAreWeGood(Boolean areWeGood) {
    this.areWeGood = areWeGood;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public WebPageStatus setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
    return this;
  }

  @Override
  public String toString() {
    return "WebPageStatus{" +
        "htmlConfigMap='" + htmlConfigMap + '\'' +
        ", areWeGood='" + areWeGood + '\'' +
        ", errorMessage='" + errorMessage + '\'' +
        '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    WebPageStatus that = (WebPageStatus) o;
    return Objects.equals(htmlConfigMap, that.htmlConfigMap)
        && Objects.equals(areWeGood, that.areWeGood)
        && Objects.equals(errorMessage, that.errorMessage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(htmlConfigMap, areWeGood, errorMessage);
  }
}
