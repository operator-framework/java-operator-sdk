package io.javaoperatorsdk.operator.sample;

public class WebPageSpec {

  private String html;

  public String getHtml() {
    return html;
  }

  public void setHtml(String html) {
    this.html = html;
  }

  @Override
  public String toString() {
    return "WebPageSpec{" +
            "html='" + html + '\'' +
            '}';
  }
}
