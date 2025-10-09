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
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

@SuppressWarnings("rawtypes")
public class ReconcilerUtils {

  private static final String FINALIZER_NAME_SUFFIX = "/finalizer";
  protected static final String MISSING_GROUP_SUFFIX = ".javaoperatorsdk.io";
  private static final String GET_SPEC = "getSpec";
  private static final String SET_SPEC = "setSpec";
  private static final String SET_STATUS = "setStatus";
  private static final String GET_STATUS = "getStatus";
  private static final Pattern API_URI_PATTERN =
      Pattern.compile(".*http(s?)://[^/]*/api(s?)/(\\S*).*"); // NOSONAR: input is controlled

  // prevent instantiation of util class
  private ReconcilerUtils() {}

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
}
