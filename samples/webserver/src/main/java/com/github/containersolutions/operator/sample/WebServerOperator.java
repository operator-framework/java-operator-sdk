package com.github.containersolutions.operator.sample;

import com.github.containersolutions.operator.Operator;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.takes.facets.fork.FkRegex;
import org.takes.facets.fork.TkFork;
import org.takes.http.Exit;
import org.takes.http.FtBasic;

import java.io.IOException;

public class WebServerOperator {

    private static final Logger log = LoggerFactory.getLogger(WebServerOperator.class);

    public static void main(String[] args) throws IOException {
        log.info("WebServer Operator starting!");

        Config config = new ConfigBuilder().withNamespace(null).build();
        Operator operator = new Operator(new DefaultKubernetesClient(config));
        operator.registerControllerForAllNamespaces(new WebServerController());

        new FtBasic(
                new TkFork(new FkRegex("/health", "ALL GOOD!")), 8080
        ).start(Exit.NEVER);
    }
}
