package io.javaoperatorsdk.operator.baseapi.ssaissue.finalizer;

import java.util.List;

public class SSAFinalizerIssueSpec {

  private String value;

  private List<String> list = null;

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public List<String> getList() {
    return list;
  }

  public void setList(List<String> list) {
    this.list = list;
  }
}
