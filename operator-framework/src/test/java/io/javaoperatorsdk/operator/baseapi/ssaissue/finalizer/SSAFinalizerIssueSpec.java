package io.javaoperatorsdk.operator.baseapi.ssaissue.finalizer;

import java.util.ArrayList;
import java.util.List;

public class SSAFinalizerIssueSpec {

  private String value;

  // List is initialized, that at the end becomes problematic when adding the finalizer
  // If the list is not initialized like this (this its null) it works fine
  private List<String> list = new ArrayList<>();

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
