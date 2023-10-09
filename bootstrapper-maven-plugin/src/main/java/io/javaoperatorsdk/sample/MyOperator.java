package io.javaoperatorsdk.sample;

import io.javaoperatorsdk.operator.Operator;

public class MyOperator {


    public static void main(String[] args) {
        Operator operator = new Operator();
        operator.register(new MyReconciler());
        operator.start();
        System.out.println("started");
    }

}
