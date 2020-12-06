package io.javaoperatorsdk.operator;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
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


class CustomResourceToCustomResourceDoneableProvider {
    private static final Logger log = LoggerFactory.getLogger(CustomResourceToCustomResourceDoneableProvider.class);

    static Map<Class<? extends CustomResource>, Class<? extends CustomResourceDoneable>> provide(final String resourcePath) {
        Map<Class<? extends CustomResource>, Class<? extends CustomResourceDoneable>> result = new HashMap();
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
                            throw new IllegalStateException(String.format("%s is not valid Mapping metadata, defined in %s", clazzPair, url.toString()));
                        }

                        result.put(
                                (Class<? extends CustomResource>) ClassUtils.getClass(classNames[0]),
                                (Class<? extends CustomResourceDoneable>) ClassUtils.getClass(classNames[1])
                        );
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            log.debug("Loaded Controller to CustomResource mappings {}", result);
            return result;
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
