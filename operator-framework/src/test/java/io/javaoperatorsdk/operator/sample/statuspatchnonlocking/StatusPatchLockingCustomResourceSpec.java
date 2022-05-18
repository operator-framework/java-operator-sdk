package io.javaoperatorsdk.operator.sample.statuspatchnonlocking;

public class StatusPatchLockingCustomResourceSpec {

  private boolean messageInStatus = true;

  public boolean isMessageInStatus() {
    return messageInStatus;
  }

  public StatusPatchLockingCustomResourceSpec setMessageInStatus(boolean messageInStatus) {
    this.messageInStatus = messageInStatus;
    return this;
  }
}
