package com.github.containersolutions.operator.sample;

import com.github.containersolutions.operator.Operator;

public class Runner {

    public static void main(String[] args) {
        Operator operator = Operator.initializeFromEnvironment();
        operator.registerController(new CustomServiceController());
    }
}
