package com.github.containersolutions.operator.sample;

import com.github.containersolutions.operator.Operator;
import com.github.containersolutions.operator.OperatorConfig;

public class Runner {

    public static void main(String[] args) {
        Operator operator = new Operator(new OperatorConfig().setTrustSelfSignedCertificates(true));
        operator.registerController(new CustomServiceController());
    }
}
