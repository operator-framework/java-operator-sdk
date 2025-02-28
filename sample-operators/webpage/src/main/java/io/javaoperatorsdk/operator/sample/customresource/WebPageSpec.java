package io.javaoperatorsdk.operator.sample.customresource;

public class WebPageSpec {

  private String html;
  private Boolean exposed = false;

  public String getHtml() {
    return html;
  }

  public void setHtml(String html) {
    this.html = html;
  }

  public Boolean getExposed() {
    return exposed;
  }

  public WebPageSpec setExposed(Boolean exposed) {
    this.exposed = exposed;
    return this;
  }

  @Override
  public String toString() {
    return "WebPageSpec{" + "html='" + html + '\'' + '}';
  }
}
