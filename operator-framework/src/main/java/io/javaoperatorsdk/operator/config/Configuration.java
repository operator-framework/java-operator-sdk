package io.javaoperatorsdk.operator.config;

public class Configuration {
    private ClientConfiguration client;
    private OperatorConfiguration operator;
    
    public ClientConfiguration getClientConfiguration() {
        return client;
    }
    
    public OperatorConfiguration getOperatorConfiguration() {
        return operator;
    }
    
    public static Configuration defaultConfiguration() {
        return new Configuration();
    }
}
