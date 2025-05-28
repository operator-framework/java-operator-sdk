package io.javaoperatorsdk.operator.config.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ClassMappingProvider {

  private static final Logger log = LoggerFactory.getLogger(ClassMappingProvider.class);

  @SuppressWarnings("unchecked")
  static <T, V> Map<T, V> provide(final String resourcePath, T key, V value) {
    Map<T, V> result = new HashMap<>();
    try {
      final var classLoader = Thread.currentThread().getContextClassLoader();
      final Enumeration<URL> resourcesMetadataList = classLoader.getResources(resourcePath);
      for (Iterator<URL> it = resourcesMetadataList.asIterator(); it.hasNext(); ) {
        URL url = it.next();

        List<String> classNamePairs = retrieveClassNamePairs(url);
        classNamePairs.forEach(
            clazzPair -> {
              try {
                final String[] classNames = clazzPair.split(",");
                if (classNames.length != 2) {
                  throw new IllegalStateException(
                      String.format(
                          "%s is not valid Mapping metadata, defined in %s", clazzPair, url));
                }

                result.put(
                    (T) ClassUtils.getClass(classNames[0]), (V) ClassUtils.getClass(classNames[1]));
              } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
              }
            });
      }
      log.debug("Loaded Controller to resource mappings {}", result);
      return result;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static List<String> retrieveClassNamePairs(URL url) throws IOException {
    try (BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()))) {
      return br.lines().collect(Collectors.toList());
    }
  }
}
