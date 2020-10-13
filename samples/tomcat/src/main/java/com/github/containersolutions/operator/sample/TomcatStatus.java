package com.github.containersolutions.operator.sample;

public class TomcatStatus {

    private Integer readyReplicas = 0;

    public Integer getReadyReplicas() {
        return readyReplicas;
    }

    public void setReadyReplicas(Integer readyReplicas) {
        this.readyReplicas = readyReplicas;
    }
}
