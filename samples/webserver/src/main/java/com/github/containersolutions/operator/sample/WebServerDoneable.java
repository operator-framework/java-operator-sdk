package com.github.containersolutions.operator.sample;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;

public class WebServerDoneable extends CustomResourceDoneable<WebServer> {
    public WebServerDoneable(WebServer resource, Function<WebServer, WebServer> function) {
        super(resource, function);
    }
}
