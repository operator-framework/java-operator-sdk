package io.javaoperatorsdk.operator.config;

import java.util.Optional;

public interface ClientConfiguration {
    ClientConfiguration DEFAULT = new ClientConfiguration() {
    };
    
    default boolean isOpenshift() {
        return false;
    }
    
    default Optional<String> getUsername() {
        return Optional.empty();
    }
    
    default Optional<String> getPassword() {
        return Optional.empty();
    }
    
    default Optional<String> getMasterUrl() {
        return Optional.empty();
    }
    
    default boolean isTrustSelfSignedCertificates() {
        return false;
    }
}
