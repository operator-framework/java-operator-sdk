package com.github.containersolutions.operator.sample;

import com.github.containersolutions.operator.Operator;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.takes.facets.fork.FkRegex;
import org.takes.facets.fork.TkFork;
import org.takes.http.Exit;
import org.takes.http.FtBasic;

import java.io.IOException;

public class NginxWwwOperator {

    private static final Logger log = LoggerFactory.getLogger(NginxWwwOperator.class);

    public static void main(String[] args) throws IOException {
        log.info("NginxWww Operator starting");
        var client = new DefaultKubernetesClient();
        Operator operator = new Operator(client);
        operator.registerController(new NginxWwwController());

        client.pods().inAnyNamespace().list().getItems()
                .forEach(item -> log.info("{} in {}", item.getMetadata().getName(),
                        item.getMetadata().getNamespace()));

        new FtBasic(
                new TkFork(new FkRegex("/health", "ALL GOOD!")), 8080
        ).start(Exit.NEVER);
    }
}
