package io.javaoperatorsdk.operator.baseapi.statuscache;

public class StatusPatchPrimaryCacheSpec {

  private boolean messageInStatus = true;

  public boolean isMessageInStatus() {
    return messageInStatus;
  }

  public StatusPatchPrimaryCacheSpec setMessageInStatus(boolean messageInStatus) {
    this.messageInStatus = messageInStatus;
    return this;
  }
}
