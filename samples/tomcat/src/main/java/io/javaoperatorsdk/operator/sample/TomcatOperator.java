package io.javaoperatorsdk.operator.sample;

import java.io.IOException;

import io.javaoperatorsdk.operator.Operator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.takes.facets.fork.FkRegex;
import org.takes.facets.fork.TkFork;
import org.takes.http.Exit;
import org.takes.http.FtBasic;

public class TomcatOperator {

    private static final Logger log = LoggerFactory.getLogger(TomcatOperator.class);

    public static void main(String[] args) throws IOException {
        Operator operator = new Operator();
    
        TomcatController tomcatController = new TomcatController();
        operator.registerController(tomcatController);
        tomcatController.setTomcatOperations(operator.getCustomResourceClients(Tomcat.class));
    
        operator.registerController(new WebappController());
    
    
        new FtBasic(
            new TkFork(new FkRegex("/health", "ALL GOOD.")), 8080
        ).start(Exit.NEVER);
    }
}
