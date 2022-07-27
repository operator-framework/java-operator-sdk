package io.javaoperatorsdk.operator.sample;

import java.util.Objects;

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
    return "WebPageSpec{" +
        "html='" + html + '\'' +
        '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    WebPageSpec that = (WebPageSpec) o;
    return Objects.equals(html, that.html) && Objects.equals(exposed, that.exposed);
  }

  @Override
  public int hashCode() {
    return Objects.hash(html, exposed);
  }
}
