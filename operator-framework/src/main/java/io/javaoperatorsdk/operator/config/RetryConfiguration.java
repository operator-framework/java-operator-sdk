package io.javaoperatorsdk.operator.config;

public class RetryConfiguration {
    private Integer maxAttempts;
    private Long initialInterval;
    private Double intervalMultiplier;
    private Long maxInterval;
    private Long maxElapsedTime;
    
    public Integer getMaxAttempts() {
        return maxAttempts;
    }
    
    public RetryConfiguration setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
        return this;
    }
    
    public Long getInitialInterval() {
        return initialInterval;
    }
    
    public RetryConfiguration setInitialInterval(Long initialInterval) {
        this.initialInterval = initialInterval;
        return this;
    }
    
    public Double getIntervalMultiplier() {
        return intervalMultiplier;
    }
    
    public RetryConfiguration setIntervalMultiplier(Double intervalMultiplier) {
        this.intervalMultiplier = intervalMultiplier;
        return this;
    }
    
    public Long getMaxInterval() {
        return maxInterval;
    }
    
    public RetryConfiguration setMaxInterval(Long maxInterval) {
        this.maxInterval = maxInterval;
        return this;
    }
    
    public Long getMaxElapsedTime() {
        return maxElapsedTime;
    }
    
    public RetryConfiguration setMaxElapsedTime(Long maxElapsedTime) {
        this.maxElapsedTime = maxElapsedTime;
        return this;
    }
}
