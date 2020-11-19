package io.javaoperatorsdk.operator.springboot.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "operator.kubernetes.client")
public class OperatorProperties {

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

    public String getUsername() {
        return username;
    }

    public OperatorProperties setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public OperatorProperties setPassword(String password) {
        this.password = password;
        return this;
    }

    public String getMasterUrl() {
        return masterUrl;
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
