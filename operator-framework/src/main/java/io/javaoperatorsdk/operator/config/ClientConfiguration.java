package io.javaoperatorsdk.operator.config;

public class ClientConfiguration {
    private boolean openshift = false;
    private String username;
    private String password;
    private String masterUrl;
    private boolean trustSelfSignedCertificates = false;
    
    public boolean isOpenshift() {
        return openshift;
    }
    
    public ClientConfiguration setOpenshift(boolean openshift) {
        this.openshift = openshift;
        return this;
    }
    
    public String getUsername() {
        return username;
    }
    
    public ClientConfiguration setUsername(String username) {
        this.username = username;
        return this;
    }
    
    public String getPassword() {
        return password;
    }
    
    public ClientConfiguration setPassword(String password) {
        this.password = password;
        return this;
    }
    
    public String getMasterUrl() {
        return masterUrl;
    }
    
    public ClientConfiguration setMasterUrl(String masterUrl) {
        this.masterUrl = masterUrl;
        return this;
    }
    
    public boolean isTrustSelfSignedCertificates() {
        return trustSelfSignedCertificates;
    }
    
    public ClientConfiguration setTrustSelfSignedCertificates(boolean trustSelfSignedCertificates) {
        this.trustSelfSignedCertificates = trustSelfSignedCertificates;
        return this;
    }
}
