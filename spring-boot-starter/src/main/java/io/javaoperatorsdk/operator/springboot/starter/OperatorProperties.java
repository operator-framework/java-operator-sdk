package io.javaoperatorsdk.operator.springboot.starter;

import java.util.Optional;

import io.javaoperatorsdk.operator.config.ClientConfiguration;

public class OperatorProperties implements ClientConfiguration {
    
    private boolean openshift = false;
    private String username;
    private String password;
    private String masterUrl;
    private boolean trustSelfSignedCertificates = false;
    
    public boolean isOpenshift() {
        return openshift;
    }
    
    public OperatorProperties setOpenshift(boolean openshift) {
        this.openshift = openshift;
        return this;
    }
    
    public Optional<String> getUsername() {
    return Optional.ofNullable(username);
    }
    
    public OperatorProperties setUsername(String username) {
        this.username = username;
        return this;
    }
    
    public Optional<String> getPassword() {
    return Optional.ofNullable(password);
    }
    
    public OperatorProperties setPassword(String password) {
        this.password = password;
        return this;
    }
    
    public Optional<String> getMasterUrl() {
    return Optional.ofNullable(masterUrl);
    }
    
    public OperatorProperties setMasterUrl(String masterUrl) {
        this.masterUrl = masterUrl;
        return this;
    }
    
    public boolean isTrustSelfSignedCertificates() {
        return trustSelfSignedCertificates;
    }

  public OperatorProperties setTrustSelfSignedCertificates(boolean trustSelfSignedCertificates) {
    this.trustSelfSignedCertificates = trustSelfSignedCertificates;
    return this;
  }
}
