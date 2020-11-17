package io.javaoperatorsdk.operator.sample;

import java.io.IOException;

import io.javaoperatorsdk.operator.Operator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.takes.facets.fork.FkRegex;
import org.takes.facets.fork.TkFork;
import org.takes.http.Exit;
import org.takes.http.FtBasic;

public class WebServerOperator {

    private static final Logger log = LoggerFactory.getLogger(WebServerOperator.class);

    public static void main(String[] args) throws IOException {
        log.info("WebServer Operator starting!");
    
        Operator operator = new Operator();
        operator.registerController(new WebServerController());
    
        new FtBasic(
            new TkFork(new FkRegex("/health", "ALL GOOD!")), 8080
        ).start(Exit.NEVER);
    }
}
