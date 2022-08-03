package io.javaoperatorsdk.operator.sample;

import java.util.ArrayList;
import java.util.List;

public class LeaderElectionTestStatus {

  private List<String> reconciledBy;

  public List<String> getReconciledBy() {
    if (reconciledBy == null) {
      reconciledBy = new ArrayList<>();
    }
    return reconciledBy;
  }

  public LeaderElectionTestStatus setReconciledBy(List<String> reconciledBy) {
    this.reconciledBy = reconciledBy;
    return this;
  }
}
