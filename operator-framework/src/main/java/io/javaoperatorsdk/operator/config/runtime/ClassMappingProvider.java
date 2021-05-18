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

  static <T, V> Map<T, V> provide(final String resourcePath, T key, V value) {
    Map<T, V> result = new HashMap();
    try {
      final var classLoader = ClassMappingProvider.class.getClassLoader();
      final Enumeration<URL> customResourcesMetadataList = classLoader.getResources(resourcePath);
      for (Iterator<URL> it = customResourcesMetadataList.asIterator(); it.hasNext(); ) {
        URL url = it.next();

        List<String> classNamePairs = retrieveClassNamePairs(url);
        classNamePairs.forEach(
            clazzPair -> {
              try {
                final String[] classNames = clazzPair.split(",");
                if (classNames.length != 2) {
                  throw new IllegalStateException(
                      String.format(
                          "%s is not valid Mapping metadata, defined in %s",
                          clazzPair, url.toString()));
                }

                result.put(
                    (T) ClassUtils.getClass(classLoader, classNames[0]),
                    (V) ClassUtils.getClass(classLoader, classNames[1]));
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
    return new BufferedReader(new InputStreamReader(url.openStream()))
        .lines()
        .collect(Collectors.toList());
  }
}
