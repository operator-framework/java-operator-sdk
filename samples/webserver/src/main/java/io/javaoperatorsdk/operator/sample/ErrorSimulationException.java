package com.github.containersolutions.operator.sample;

public class ErrorSimulationException extends RuntimeException {

    public ErrorSimulationException(String message) {
        super(message);
    }
}
