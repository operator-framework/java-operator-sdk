package io.javaoperatorsdk.operator.springboot.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "operator.kubernetes.client")
public class ClientProperties {
    
    private boolean openshift = false;
    private String username;
    private String password;
    private String masterUrl;
    private boolean trustSelfSignedCertificates = false;
    
    public boolean isOpenshift() {
        return openshift;
    }
    
    public ClientProperties setOpenshift(boolean openshift) {
        this.openshift = openshift;
        return this;
    }

    public String getUsername() {
        return username;
    }
    
    public ClientProperties setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getPassword() {
        return password;
    }
    
    public ClientProperties setPassword(String password) {
        this.password = password;
        return this;
    }

    public String getMasterUrl() {
        return masterUrl;
    }
    
    public ClientProperties setMasterUrl(String masterUrl) {
        this.masterUrl = masterUrl;
        return this;
    }

    public boolean isTrustSelfSignedCertificates() {
        return trustSelfSignedCertificates;
    }
    
    public ClientProperties setTrustSelfSignedCertificates(boolean trustSelfSignedCertificates) {
        this.trustSelfSignedCertificates = trustSelfSignedCertificates;
        return this;
    }
}
