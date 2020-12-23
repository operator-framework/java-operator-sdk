package io.javaoperatorsdk.operator.springboot.starter.test;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("io.javaoperatorsdk.test")
public class TestConfigurationProperties {

    private List<String> crdPaths = new ArrayList<>();

    public List<String> getCrdPaths() {
        return crdPaths;
    }

    public void setCrdPaths(List<String> crdPaths) {
        this.crdPaths = crdPaths;
    }
}
