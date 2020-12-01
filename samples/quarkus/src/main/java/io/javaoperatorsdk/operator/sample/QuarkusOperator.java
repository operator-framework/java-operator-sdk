package io.javaoperatorsdk.operator.sample;

import javax.inject.Inject;

import io.javaoperatorsdk.operator.Operator;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class QuarkusOperator implements QuarkusApplication {
    
    @Inject
    Operator operator;
    
    public static void main(String... args) {
        Quarkus.run(QuarkusOperator.class, args);
    }
    
    @Override
    public int run(String... args) throws Exception {
        System.out.println("operator = " + operator);
        Quarkus.waitForExit();
        return 0;
    }
}