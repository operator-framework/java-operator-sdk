package io.javaoperatorsdk.operator.sample;

import io.javaoperatorsdk.operator.Operator;

public class PureJavaApplicationRunner {

    public static void main(String[] args) {
        Operator operator = new Operator();
        operator.registerController(new CustomServiceController(operator.getClient()));
    }
}
