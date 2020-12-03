package io.javaoperatorsdk.operator;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.ResourceController;
import org.apache.commons.lang3.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;


class ControllerToCustomResourceMappingsProvider {
    private static final Logger log = LoggerFactory.getLogger(ControllerUtils.class);

    static Map<Class<? extends ResourceController>, Class<? extends CustomResource>> provide(final String resourcePath) {
        Map<Class<? extends ResourceController>, Class<? extends CustomResource>> controllerToCustomResourceMappings = new HashMap();
        try {
            final var classLoader = Thread.currentThread().getContextClassLoader();
            final Enumeration<URL> customResourcesMetadataList = classLoader.getResources(resourcePath);
            for (Iterator<URL> it = customResourcesMetadataList.asIterator(); it.hasNext(); ) {
                URL url = it.next();

                List<String> classNamePairs = retrieveClassNamePairs(url);
                classNamePairs.forEach(clazzPair -> {
                    try {
                        final String[] classNames = clazzPair.split(",");
                        if (classNames.length != 2) {
                            throw new IllegalStateException(String.format("%s is not valid CustomResource metadata defined in %s", clazzPair, url.toString()));
                        }

                        controllerToCustomResourceMappings.put(
                                (Class<? extends ResourceController>) ClassUtils.getClass(classNames[0]),
                                (Class<? extends CustomResource>) ClassUtils.getClass(classNames[1])
                        );
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            log.debug("Loaded Controller to CustomResource mappings {}", controllerToCustomResourceMappings);
            return controllerToCustomResourceMappings;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> retrieveClassNamePairs(URL url) throws IOException {
        return new BufferedReader(
                new InputStreamReader(url.openStream())
        ).lines().collect(Collectors.toList());
    }
}
