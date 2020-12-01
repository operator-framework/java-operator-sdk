package io.javaoperatorsdk.operator.processing.annotation;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.StandardLocation;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.javaoperatorsdk.operator.ControllerUtils.CONTROLLERS_RESOURCE_PATH;

public class ControllersResourceWriter {
    private Map<String, String> mappings = new ConcurrentHashMap<>();
    private final ProcessingEnvironment processingEnvironment;

    public ControllersResourceWriter(ProcessingEnvironment processingEnvironment) {
        this.processingEnvironment = processingEnvironment;
    }

    public ControllersResourceWriter loadExistingMappings() {
        try {
            final var readonlyResource = processingEnvironment
                    .getFiler()
                    .getResource(StandardLocation.CLASS_OUTPUT, "", CONTROLLERS_RESOURCE_PATH);

            final var existingLines = new BufferedReader(new InputStreamReader(readonlyResource.openInputStream()))
                    .lines()
                    .map(l -> l.split(","))
                    .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1]));
            mappings.putAll(existingLines);
        } catch (IOException e) {
        }
        return this;
    }

    public ControllersResourceWriter add(String controllerClassName, String customResourceTypeName) {
        this.mappings.put(controllerClassName, customResourceTypeName);
        return this;
    }

    public void flush() {
        try {
            final var resource = processingEnvironment
                    .getFiler()
                    .createResource(StandardLocation.CLASS_OUTPUT, "", CONTROLLERS_RESOURCE_PATH);
            final var printWriter = new PrintWriter(resource.openOutputStream());
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                printWriter.println(entry.getKey() + "," + entry.getValue());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
