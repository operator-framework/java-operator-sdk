package io.javaoperatorsdk.operator.sample.errorstatushandler;

import java.util.ArrayList;
import java.util.List;

public class ErrorStatusHandlerTestCustomResourceStatus {

  private List<String> messages;

  public List<String> getMessages() {
    if (messages == null) {
      messages = new ArrayList<>();
    }
    return messages;
  }

  public ErrorStatusHandlerTestCustomResourceStatus setMessages(List<String> messages) {
    this.messages = messages;
    return this;
  }
}
