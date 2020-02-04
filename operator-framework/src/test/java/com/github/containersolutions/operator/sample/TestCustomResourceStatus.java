package com.github.containersolutions.operator.sample;

public class TestCustomResourceStatus {

    private String configMapStatus;

    public String getConfigMapStatus() {
        return configMapStatus;
    }

    public void setConfigMapStatus(String configMapStatus) {
        this.configMapStatus = configMapStatus;
    }

    @Override
    public String toString() {
        return "TestCustomResourceStatus{" +
                "configMapStatus='" + configMapStatus + '\'' +
                '}';
    }
}
