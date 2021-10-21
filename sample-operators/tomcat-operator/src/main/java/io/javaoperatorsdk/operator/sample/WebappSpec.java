package io.javaoperatorsdk.operator.sample;

public class WebappSpec {

  private String url;

  private String contextPath;

  private String tomcat;

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getContextPath() {
    return contextPath;
  }

  public void setContextPath(String contextPath) {
    this.contextPath = contextPath;
  }

  public String getTomcat() {
    return tomcat;
  }

  public void setTomcat(String tomcat) {
    this.tomcat = tomcat;
  }
}
