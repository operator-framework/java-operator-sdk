/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import io.fabric8.kubernetes.api.builder.Builder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.NonComparableResourceVersionException;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

@SuppressWarnings("rawtypes")
public class ReconcilerUtilsInternal {

  private static final String FINALIZER_NAME_SUFFIX = "/finalizer";
  protected static final String MISSING_GROUP_SUFFIX = ".javaoperatorsdk.io";
  private static final String GET_SPEC = "getSpec";
  private static final String SET_SPEC = "setSpec";
  private static final String SET_STATUS = "setStatus";
  private static final String GET_STATUS = "getStatus";
  private static final Pattern API_URI_PATTERN =
      Pattern.compile(".*http(s?)://[^/]*/api(s?)/(\\S*).*"); // NOSONAR: input is controlled

  // prevent instantiation of util class
  private ReconcilerUtilsInternal() {}

  public static boolean isFinalizerValid(String finalizer) {
    return HasMetadata.validateFinalizer(finalizer);
  }

  public static String getResourceTypeNameWithVersion(Class<? extends HasMetadata> resourceClass) {
    final var version = HasMetadata.getVersion(resourceClass);
    return getResourceTypeName(resourceClass) + "/" + version;
  }

  public static String getResourceTypeName(Class<? extends HasMetadata> resourceClass) {
    return HasMetadata.getFullResourceName(resourceClass);
  }

  public static String getDefaultFinalizerName(Class<? extends HasMetadata> resourceClass) {
    return getDefaultFinalizerName(getResourceTypeName(resourceClass));
  }

  public static String getDefaultFinalizerName(String resourceName) {
    // resource names for historic resources such as Pods are missing periods and therefore do not
    // constitute valid domain names as mandated by Kubernetes so generate one that does
    if (resourceName.indexOf('.') < 0) {
      resourceName = resourceName + MISSING_GROUP_SUFFIX;
    }
    return resourceName + FINALIZER_NAME_SUFFIX;
  }

  public static String getNameFor(Class<? extends Reconciler> reconcilerClass) {
    // if the reconciler annotation has a name attribute, use it
    final var annotation = reconcilerClass.getAnnotation(ControllerConfiguration.class);
    if (annotation != null) {
      final var name = annotation.name();
      if (!Constants.NO_VALUE_SET.equals(name)) {
        return name;
      }
    }
    // otherwise, use the lower-cased full class name
    return getDefaultNameFor(reconcilerClass);
  }

  public static String getNameFor(Reconciler reconciler) {
    return getNameFor(reconciler.getClass());
  }

  public static String getDefaultNameFor(Reconciler reconciler) {
    return getDefaultNameFor(reconciler.getClass());
  }

  public static String getDefaultNameFor(Class<? extends Reconciler> reconcilerClass) {
    return getDefaultReconcilerName(reconcilerClass.getSimpleName());
  }

  public static String getDefaultReconcilerName(String reconcilerClassName) {
    // if the name is fully qualified, extract the simple class name
    final var lastDot = reconcilerClassName.lastIndexOf('.');
    if (lastDot > 0) {
      reconcilerClassName = reconcilerClassName.substring(lastDot + 1);
    }
    return reconcilerClassName.toLowerCase(Locale.ROOT);
  }

  public static boolean specsEqual(HasMetadata r1, HasMetadata r2) {
    return getSpec(r1).equals(getSpec(r2));
  }

  // will be replaced with: https://github.com/fabric8io/kubernetes-client/issues/3816
  public static Object getSpec(HasMetadata resource) {
    // optimize CustomResource case
    if (resource instanceof CustomResource cr) {
      return cr.getSpec();
    }

    return getSpecOrStatus(resource, GET_SPEC);
  }

  public static Object getStatus(HasMetadata resource) {
    // optimize CustomResource case
    if (resource instanceof CustomResource cr) {
      return cr.getStatus();
    }
    return getSpecOrStatus(resource, GET_STATUS);
  }

  private static Object getSpecOrStatus(HasMetadata resource, String getMethod) {
    try {
      Method getSpecMethod = resource.getClass().getMethod(getMethod);
      return getSpecMethod.invoke(resource);
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      throw noMethodException(resource, e, getMethod);
    }
  }

  @SuppressWarnings("unchecked")
  public static Object setSpec(HasMetadata resource, Object spec) {
    // optimize CustomResource case
    if (resource instanceof CustomResource cr) {
      cr.setSpec(spec);
      return null;
    }

    return setSpecOrStatus(resource, spec, SET_SPEC);
  }

  @SuppressWarnings("unchecked")
  public static Object setStatus(HasMetadata resource, Object status) {
    // optimize CustomResource case
    if (resource instanceof CustomResource cr) {
      cr.setStatus(status);
      return null;
    }
    return setSpecOrStatus(resource, status, SET_STATUS);
  }

  private static Object setSpecOrStatus(
      HasMetadata resource, Object spec, String setterMethodName) {
    try {
      Class<? extends HasMetadata> resourceClass = resource.getClass();

      // if given spec is null, find the method just using its name
      Method setMethod;
      if (spec != null) {
        setMethod = resourceClass.getMethod(setterMethodName, spec.getClass());
      } else {
        setMethod =
            Arrays.stream(resourceClass.getMethods())
                .filter(method -> setterMethodName.equals(method.getName()))
                .findFirst()
                .orElseThrow(() -> noMethodException(resource, null, setterMethodName));
      }

      return setMethod.invoke(resource, spec);
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      throw noMethodException(resource, e, setterMethodName);
    }
  }

  private static IllegalStateException noMethodException(
      HasMetadata resource, ReflectiveOperationException e, String methodName) {
    return new IllegalStateException(
        "No method: " + methodName + " found on resource " + resource.getClass().getName(), e);
  }

  public static <T> T loadYaml(Class<T> clazz, Class loader, String yaml) {
    try (InputStream is = loader.getResourceAsStream(yaml)) {
      if (Builder.class.isAssignableFrom(clazz)) {
        return BuilderUtils.newBuilder(
            clazz, Serialization.unmarshal(is, BuilderUtils.builderTargetType(clazz)));
      }
      return Serialization.unmarshal(is, clazz);
    } catch (IOException ex) {
      throw new IllegalStateException("Cannot find yaml on classpath: " + yaml);
    }
  }

  public static void handleKubernetesClientException(Exception e, String resourceTypeName) {
    if (e instanceof MissingCRDException) {
      throw ((MissingCRDException) e);
    }

    if (e instanceof KubernetesClientException ke) {
      // only throw MissingCRDException if the 404 error occurs on the target CRD
      if (404 == ke.getCode()
          && (resourceTypeName.equals(ke.getFullResourceName())
              || matchesResourceType(resourceTypeName, ke))) {
        throw new MissingCRDException(resourceTypeName, ke.getVersion(), e.getMessage(), e);
      }
    }
  }

  private static boolean matchesResourceType(
      String resourceTypeName, KubernetesClientException exception) {
    final var fullResourceName = exception.getFullResourceName();
    if (fullResourceName != null) {
      return resourceTypeName.equals(fullResourceName);
    } else {
      // extract matching information from URI in the message if available
      final var message = exception.getMessage();
      final var regex = API_URI_PATTERN.matcher(message);
      if (regex.matches()) {
        var group = regex.group(3);
        if (group.endsWith(".")) {
          group = group.substring(0, group.length() - 1);
        }
        final var segments =
            Arrays.stream(group.split("/")).filter(Predicate.not(String::isEmpty)).toList();
        if (segments.size() != 3) {
          return false;
        }
        final var targetResourceName = segments.get(2) + "." + segments.get(0);
        return resourceTypeName.equals(targetResourceName);
      }
    }
    return false;
  }

  /**
   * Compares resource versions of two resources. This is a convenience method that extracts the
   * resource versions from the metadata and delegates to {@link
   * #validateAndCompareResourceVersions(String, String)}.
   *
   * @param h1 first resource
   * @param h2 second resource
   * @return negative if h1 is older, zero if equal, positive if h1 is newer
   * @throws NonComparableResourceVersionException if either resource version is invalid
   */
  public static int validateAndCompareResourceVersions(HasMetadata h1, HasMetadata h2) {
    return validateAndCompareResourceVersions(
        h1.getMetadata().getResourceVersion(), h2.getMetadata().getResourceVersion());
  }

  /**
   * Compares the resource versions of two Kubernetes resources.
   *
   * <p>This method extracts the resource versions from the metadata of both resources and delegates
   * to {@link #compareResourceVersions(String, String)} for the actual comparison.
   *
   * @param h1 the first resource to compare
   * @param h2 the second resource to compare
   * @return a negative integer if h1's version is less than h2's version, zero if they are equal,
   *     or a positive integer if h1's version is greater than h2's version
   * @see #compareResourceVersions(String, String)
   */
  public static int compareResourceVersions(HasMetadata h1, HasMetadata h2) {
    return compareResourceVersions(
        h1.getMetadata().getResourceVersion(), h2.getMetadata().getResourceVersion());
  }

  /**
   * Compares two resource version strings using a length-first, then lexicographic comparison
   * algorithm.
   *
   * <p>The comparison is performed in two steps:
   *
   * <ol>
   *   <li>First, compare the lengths of the version strings. A longer version string is considered
   *       greater than a shorter one. This works correctly for numeric versions because larger
   *       numbers have more digits (e.g., "100" > "99").
   *   <li>If the lengths are equal, perform a character-by-character lexicographic comparison until
   *       a difference is found.
   * </ol>
   *
   * <p>This algorithm is more efficient than parsing the versions as numbers, especially for
   * Kubernetes resource versions which are typically monotonically increasing numeric strings.
   *
   * <p><strong>Note:</strong> This method does not validate that the input strings are numeric. For
   * validated numeric comparison, use {@link #validateAndCompareResourceVersions(String, String)}.
   *
   * @param v1 the first resource version string
   * @param v2 the second resource version string
   * @return a negative integer if v1 is less than v2, zero if they are equal, or a positive integer
   *     if v1 is greater than v2
   * @see #validateAndCompareResourceVersions(String, String)
   */
  public static int compareResourceVersions(String v1, String v2) {
    int comparison = v1.length() - v2.length();
    if (comparison != 0) {
      return comparison;
    }
    for (int i = 0; i < v2.length(); i++) {
      int comp = v1.charAt(i) - v2.charAt(i);
      if (comp != 0) {
        return comp;
      }
    }
    return 0;
  }

  /**
   * Compares two Kubernetes resource versions numerically. Kubernetes resource versions are
   * expected to be numeric strings that increase monotonically. This method assumes both versions
   * are valid numeric strings without leading zeros.
   *
   * @param v1 first resource version
   * @param v2 second resource version
   * @return negative if v1 is older, zero if equal, positive if v1 is newer
   * @throws NonComparableResourceVersionException if either resource version is empty, has leading
   *     zeros, or contains non-numeric characters
   */
  public static int validateAndCompareResourceVersions(String v1, String v2) {
    int v1Length = validateResourceVersion(v1);
    int v2Length = validateResourceVersion(v2);
    int comparison = v1Length - v2Length;
    if (comparison != 0) {
      return comparison;
    }
    for (int i = 0; i < v2Length; i++) {
      int comp = v1.charAt(i) - v2.charAt(i);
      if (comp != 0) {
        return comp;
      }
    }
    return 0;
  }

  private static int validateResourceVersion(String v1) {
    int v1Length = v1.length();
    if (v1Length == 0) {
      throw new NonComparableResourceVersionException("Resource version is empty");
    }
    for (int i = 0; i < v1Length; i++) {
      char char1 = v1.charAt(i);
      if (char1 == '0') {
        if (i == 0) {
          throw new NonComparableResourceVersionException(
              "Resource version cannot begin with 0: " + v1);
        }
      } else if (char1 < '0' || char1 > '9') {
        throw new NonComparableResourceVersionException(
            "Non numeric characters in resource version: " + v1);
      }
    }
    return v1Length;
  }
}
