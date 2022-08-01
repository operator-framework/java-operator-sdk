package io.javaoperatorsdk.operator.sample.leaderelection;

import java.util.ArrayList;
import java.util.List;

public class LeaderElectionTestCustomResourceStatus {

    private List<String> reconciledBy;

    public List<String> getReconciledBy() {
        if (reconciledBy == null) {
            reconciledBy = new ArrayList<>();
        }
        return reconciledBy;
    }

    public LeaderElectionTestCustomResourceStatus setReconciledBy(List<String> reconciledBy) {
        this.reconciledBy = reconciledBy;
        return this;
    }
}
