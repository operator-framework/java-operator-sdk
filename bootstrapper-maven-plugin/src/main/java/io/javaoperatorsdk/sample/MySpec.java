package io.javaoperatorsdk.sample;

public class MySpec {

    private String value;

    public String getValue() {
        return value;
    }

    public MySpec setValue(String value) {
        this.value = value;
        return this;
    }
}
