package io.javaoperatorsdk.operator.config;

public interface RetryConfiguration {
    int DEFAULT_MAX_ATTEMPTS = 5;
    long DEFAULT_INITIAL_INTERVAL = 2000L;
    long DEFAULT_MAX_ELAPSED_TIME = 30_000L;
    double DEFAULT_MULTIPLIER = 1.5D;
    
    default int getMaxAttempts() {
        return DEFAULT_MAX_ATTEMPTS;
    }
    
    default long getInitialInterval() {
        return DEFAULT_INITIAL_INTERVAL;
    }
    
    default double getIntervalMultiplier() {
        return DEFAULT_MULTIPLIER;
    }
    
    default long getMaxInterval() {
        return (long) (DEFAULT_INITIAL_INTERVAL * Math.pow(DEFAULT_MULTIPLIER, DEFAULT_MAX_ATTEMPTS));
    }
    
    default long getMaxElapsedTime() {
        return DEFAULT_MAX_ELAPSED_TIME;
    }
}
