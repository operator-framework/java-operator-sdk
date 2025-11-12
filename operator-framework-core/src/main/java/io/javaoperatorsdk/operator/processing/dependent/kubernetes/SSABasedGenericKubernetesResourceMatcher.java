package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ManagedFieldsEntry;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.LoggingUtils;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;

import static io.javaoperatorsdk.operator.processing.dependent.kubernetes.PodTemplateSpecSanitizer.sanitizePodTemplateSpec;

/**
 * Matches the actual state on the server vs the desired state. Based on the managedFields of SSA.
 *
 * <p>The basis of the algorithm is to extract the managed fields by converting resources to a
 * Map/List composition. The actual resource (from the server) is pruned, all the fields which are
 * not mentioned in managedFields of the target manager are removed. Some irrelevant fields are also
 * removed from the desired resource. Finally, the two resulting maps are compared for equality.
 *
 * <p>The implementation is a bit nasty since we have to deal with some specific cases of
 * managedFields formats.
 *
 * @param <R> matched resource type
 */
// https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#fieldsv1-v1-meta
// https://github.com/kubernetes-sigs/structured-merge-diff
// https://docs.aws.amazon.com/eks/latest/userguide/kubernetes-field-management.html
// see also: https://kubernetes.slack.com/archives/C0123CNN8F3/p1686141087220719
public class SSABasedGenericKubernetesResourceMatcher<R extends HasMetadata> {

  public static final String APPLY_OPERATION = "Apply";
  public static final String DOT_KEY = ".";

  private static final String F_PREFIX = "f:";
  private static final String K_PREFIX = "k:";
  private static final String V_PREFIX = "v:";
  private static final String METADATA_KEY = "metadata";
  private static final String NAME_KEY = "name";
  private static final String NAMESPACE_KEY = "namespace";
  private static final String KIND_KEY = "kind";
  private static final String API_VERSION_KEY = "apiVersion";

  @SuppressWarnings("rawtypes")
  private static final SSABasedGenericKubernetesResourceMatcher INSTANCE =
      new SSABasedGenericKubernetesResourceMatcher<>();

  private static final List<String> IGNORED_METADATA =
      List.of("creationTimestamp", "deletionTimestamp", "generation", "selfLink", "uid");

  private static final Logger log =
      LoggerFactory.getLogger(SSABasedGenericKubernetesResourceMatcher.class);

  @SuppressWarnings("unchecked")
  public static <L extends HasMetadata> SSABasedGenericKubernetesResourceMatcher<L> getInstance() {
    return INSTANCE;
  }

  @SuppressWarnings("unchecked")
  public boolean matches(R actual, R desired, Context<?> context) {
    var optionalManagedFieldsEntry =
        checkIfFieldManagerExists(actual, context.getControllerConfiguration().fieldManager());
    // If no field is managed by our controller, that means the controller hasn't touched the
    // resource yet and the resource probably doesn't match the desired state. Not matching here
    // means that the resource will need to be updated and since this will be done using SSA, the
    // fields our controller cares about will become managed by it
    if (optionalManagedFieldsEntry.isEmpty()) {
      return false;
    }

    var managedFieldsEntry = optionalManagedFieldsEntry.orElseThrow();

    var objectMapper = context.getClient().getKubernetesSerialization();
    var actualMap = objectMapper.convertValue(actual, Map.class);
    var desiredMap = objectMapper.convertValue(desired, Map.class);
    if (LoggingUtils.isNotSensitiveResource(desired)) {
      log.trace("Original actual:\n {}\n original desired:\n {}", actualMap, desiredMap);
    }

    sanitizeState(actual, desired, actualMap);
    var prunedActual = new HashMap<String, Object>(actualMap.size());
    keepOnlyManagedFields(
        prunedActual,
        actualMap,
        managedFieldsEntry.getFieldsV1().getAdditionalProperties(),
        objectMapper);

    removeIrrelevantValues(desiredMap);

    var matches = matches(prunedActual, desiredMap, actual, desired, context);
    if (!matches && log.isDebugEnabled() && LoggingUtils.isNotSensitiveResource(desired)) {
      var diff = getDiff(prunedActual, desiredMap, objectMapper);
      log.debug(
          "Diff between actual and desired state for resource: {} with name: {} in namespace: {}"
              + " is:\n"
              + "{}",
          actual.getKind(),
          actual.getMetadata().getName(),
          actual.getMetadata().getNamespace(),
          diff);
    }
    return matches;
  }

  /**
   * Compares the desired and actual resources for equality.
   *
   * <p>This method can be overridden to implement custom matching logic. The {@code actualMap} is a
   * cleaned-up version of the actual resource with managed fields and irrelevant values removed.
   *
   * @param actualMap the actual resource represented as a map
   * @param desiredMap the desired resource represented as a map
   * @param actual the actual resource object
   * @param desired the desired resource object
   * @param context the current matching context
   * @return {@code true} if the resources are equal, otherwise {@code false}
   */
  protected boolean matches(
      Map<String, Object> actualMap,
      Map<String, Object> desiredMap,
      R actual,
      R desired,
      Context<?> context) {
    return actualMap.equals(desiredMap);
  }

  private Optional<ManagedFieldsEntry> checkIfFieldManagerExists(R actual, String fieldManager) {
    var targetManagedFields =
        actual.getMetadata().getManagedFields().stream()
            // Only the apply operations are interesting for us since those were created properly be
            // SSA patch. An update can be present with same fieldManager when migrating and having
            // the same field manager name
            .filter(
                f ->
                    f.getManager().equals(fieldManager) && f.getOperation().equals(APPLY_OPERATION))
            .toList();
    if (targetManagedFields.isEmpty()) {
      log.debug(
          "No field manager exists for resource: {} with name: {} and operation {}",
          actual.getKind(),
          actual.getMetadata().getName(),
          APPLY_OPERATION);
      return Optional.empty();
    }
    // this should not happen in theory
    if (targetManagedFields.size() > 1) {
      throw new OperatorException(
          "More than one field manager exists with name: "
              + fieldManager
              + " in resource: "
              + actual.getKind()
              + " with name: "
              + actual.getMetadata().getName());
    }
    return Optional.of(targetManagedFields.get(0));
  }

  /** Correct for known issue with SSA */
  protected void sanitizeState(R actual, R desired, Map<String, Object> actualMap) {
    if (actual instanceof StatefulSet actualStatefulSet
        && desired instanceof StatefulSet desiredStatefulSet) {
      var actualSpec = actualStatefulSet.getSpec();
      var desiredSpec = desiredStatefulSet.getSpec();
      int claims = desiredSpec.getVolumeClaimTemplates().size();
      if (claims == actualSpec.getVolumeClaimTemplates().size()) {
        for (int i = 0; i < claims; i++) {
          var claim = desiredSpec.getVolumeClaimTemplates().get(i);
          if (claim.getSpec().getVolumeMode() == null) {
            Optional.ofNullable(
                    GenericKubernetesResource.get(
                        actualMap, "spec", "volumeClaimTemplates", i, "spec"))
                .map(Map.class::cast)
                .ifPresent(m -> m.remove("volumeMode"));
          }
          if (claim.getStatus() == null) {
            Optional.ofNullable(
                    GenericKubernetesResource.get(actualMap, "spec", "volumeClaimTemplates", i))
                .map(Map.class::cast)
                .ifPresent(m -> m.remove("status"));
          }
        }
      }
      sanitizePodTemplateSpec(actualMap, actualSpec.getTemplate(), desiredSpec.getTemplate());
    } else if (actual instanceof Deployment actualDeployment
        && desired instanceof Deployment desiredDeployment) {
      sanitizePodTemplateSpec(
          actualMap,
          actualDeployment.getSpec().getTemplate(),
          desiredDeployment.getSpec().getTemplate());
    } else if (actual instanceof ReplicaSet actualReplicaSet
        && desired instanceof ReplicaSet desiredReplicaSet) {
      sanitizePodTemplateSpec(
          actualMap,
          actualReplicaSet.getSpec().getTemplate(),
          desiredReplicaSet.getSpec().getTemplate());
    } else if (actual instanceof DaemonSet actualDaemonSet
        && desired instanceof DaemonSet desiredDaemonSet) {
      sanitizePodTemplateSpec(
          actualMap,
          actualDaemonSet.getSpec().getTemplate(),
          desiredDaemonSet.getSpec().getTemplate());
    }
  }

  @SuppressWarnings("unchecked")
  static void keepOnlyManagedFields(
      Map<String, Object> result,
      Map<String, Object> actualMap,
      Map<String, Object> managedFields,
      KubernetesSerialization objectMapper) {
    if (managedFields.isEmpty()) {
      result.putAll(actualMap);
      return;
    }
    for (var entry : managedFields.entrySet()) {
      var key = entry.getKey();
      if (key.startsWith(F_PREFIX)) {
        var keyInActual = keyWithoutPrefix(key);
        var managedFieldValue = (Map<String, Object>) entry.getValue();
        if (isNestedValue(managedFieldValue)) {
          var managedEntrySet = managedFieldValue.entrySet();
          // two special cases "k:" and "v:" prefixes
          if (isListKeyEntrySet(managedEntrySet)) {
            handleListKeyEntrySet(result, actualMap, objectMapper, keyInActual, managedEntrySet);
          } else if (isSetValueField(managedEntrySet)) {
            handleSetValues(result, actualMap, objectMapper, keyInActual, managedEntrySet);
          } else {
            // basically if we should traverse further
            fillResultsAndTraverseFurther(
                result,
                actualMap,
                managedFields,
                objectMapper,
                key,
                keyInActual,
                managedFieldValue);
          }
        } else {
          // this should handle the case when the value is complex in the actual map (not just a
          // simple value)
          result.put(keyInActual, actualMap.get(keyInActual));
        }
      } else {
        // .:{} is ignored, other should not be present
        if (!DOT_KEY.equals(key)) {
          throw new IllegalStateException("Key: " + key + " has no prefix: " + F_PREFIX);
        }
      }
    }
  }

  private static boolean isNestedValue(Map<?, ?> managedFieldValue) {
    return !managedFieldValue.isEmpty();
  }

  private static boolean isListKeyEntrySet(Set<Map.Entry<String, Object>> managedEntrySet) {
    return isKeyPrefixedSkippingDotKey(managedEntrySet, K_PREFIX);
  }

  private static boolean isSetValueField(Set<Map.Entry<String, Object>> managedEntrySet) {
    return isKeyPrefixedSkippingDotKey(managedEntrySet, V_PREFIX);
  }

  /**
   * Sometimes (not always) the first subfield of a managed field ("f:") is ".:{}", it looks that
   * those are added when there are more subfields of a referenced field. See test samples. Does not
   * seem to provide additional functionality, so can be just skipped for now.
   */
  private static boolean isKeyPrefixedSkippingDotKey(
      Set<Map.Entry<String, Object>> managedEntrySet, String prefix) {
    var iterator = managedEntrySet.iterator();
    var managedFieldEntry = iterator.next();
    if (managedFieldEntry.getKey().equals(DOT_KEY)) {
      managedFieldEntry = iterator.next();
    }
    return managedFieldEntry.getKey().startsWith(prefix);
  }

  /**
   * List entries referenced by key, or when "k:" prefix is used. It works in a way that it selects
   * the target element based on the field(s) in "k:" for example when there is a list of element of
   * owner references, the uid can serve as a key for a list element:
   * "k:{"uid":"1ef74cb4-dbbd-45ef-9caf-aa76186594ea"}". It selects the element and recursively
   * processes it. Note that in these lists the order matters and seems that if there are more keys
   * ("k:"), the ordering of those in the managed fields are not the same as the value order. So
   * this also explicitly orders the result based on the value order in the resource not the key
   * order in managed field.
   */
  @SuppressWarnings("unchecked")
  private static void handleListKeyEntrySet(
      Map<String, Object> result,
      Map<String, Object> actualMap,
      KubernetesSerialization objectMapper,
      String keyInActual,
      Set<Entry<String, Object>> managedEntrySet) {
    var valueList = new ArrayList<>();
    result.put(keyInActual, valueList);
    var actualValueList = (List<Map<String, Object>>) actualMap.get(keyInActual);

    if (actualValueList == null) {
      return;
    }

    var targetValuesByIndex = new TreeMap<Integer, Map<String, Object>>();
    var managedEntryByIndex = new HashMap<Integer, Map<String, Object>>();

    for (var listEntry : managedEntrySet) {
      if (DOT_KEY.equals(listEntry.getKey())) {
        continue;
      }
      var actualListEntry =
          selectListEntryBasedOnKey(
              keyWithoutPrefix(listEntry.getKey()), actualValueList, objectMapper);
      targetValuesByIndex.put(actualListEntry.getKey(), actualListEntry.getValue());
      managedEntryByIndex.put(actualListEntry.getKey(), (Map<String, Object>) listEntry.getValue());
    }

    targetValuesByIndex.forEach(
        (key, value) -> {
          var emptyResMapValue = new HashMap<String, Object>();
          valueList.add(emptyResMapValue);
          keepOnlyManagedFields(
              emptyResMapValue, value, managedEntryByIndex.get(key), objectMapper);
        });
  }

  @SuppressWarnings("unchecked")
  private static Map.Entry<Integer, Map<String, Object>> selectListEntryBasedOnKey(
      String key, List<Map<String, Object>> values, KubernetesSerialization objectMapper) {
    Map<String, Object> ids = objectMapper.unmarshal(key, Map.class);
    var possibleTargets = new ArrayList<Map<String, Object>>(1);
    int lastIndex = -1;
    for (int i = 0; i < values.size(); i++) {
      var value = values.get(i);
      if (value.entrySet().containsAll(ids.entrySet())) {
        possibleTargets.add(value);
        lastIndex = i;
      }
    }
    if (possibleTargets.isEmpty()) {
      throw new IllegalStateException(
          "Cannot find list element for key: "
              + key
              + " in map: "
              + values.stream().map(Map::keySet).toList());
    }
    if (possibleTargets.size() > 1) {
      throw new IllegalStateException(
          "More targets found in list element for key: "
              + key
              + " in map: "
              + values.stream().map(Map::keySet).toList());
    }
    return new AbstractMap.SimpleEntry<>(lastIndex, possibleTargets.get(0));
  }

  /**
   * Set values, the {@code "v:"} prefix. Form in managed fields: {@code
   * "f:some-set":{"v:1":{}},"v:2":{},"v:3":{}}.
   *
   * <p>Note that this should be just used in very rare cases, actually was not able to produce a
   * sample. Kubernetes developers who worked on this feature were not able to provide one either
   * when prompted. Basically this method just adds the values from {@code "v:<value>"} to the
   * result.
   */
  private static void handleSetValues(
      Map<String, Object> result,
      Map<String, Object> actualMap,
      KubernetesSerialization objectMapper,
      String keyInActual,
      Set<Entry<String, Object>> managedEntrySet) {
    var valueList = new ArrayList<>();
    result.put(keyInActual, valueList);
    for (var valueEntry : managedEntrySet) {
      // not clear if this can happen
      if (DOT_KEY.equals(valueEntry.getKey())) {
        continue;
      }
      var values = (List<?>) actualMap.get(keyInActual);

      if (values == null || values.isEmpty()) {
        continue;
      }

      var targetClass = (values.get(0) instanceof Map) ? null : values.get(0).getClass();
      var value = parseKeyValue(keyWithoutPrefix(valueEntry.getKey()), targetClass, objectMapper);
      valueList.add(value);
    }
  }

  public static Object parseKeyValue(
      String stringValue, Class<?> targetClass, KubernetesSerialization objectMapper) {
    var type = Objects.requireNonNullElse(targetClass, Map.class);
    return objectMapper.unmarshal(stringValue.trim(), type);
  }

  @SuppressWarnings("unchecked")
  private static void fillResultsAndTraverseFurther(
      Map<String, Object> result,
      Map<String, Object> actualMap,
      Map<String, Object> managedFields,
      KubernetesSerialization objectMapper,
      String key,
      String keyInActual,
      Object managedFieldValue) {
    var emptyMapValue = new HashMap<String, Object>();
    result.put(keyInActual, emptyMapValue);
    var actualMapValue = actualMap.getOrDefault(keyInActual, Collections.emptyMap());
    log.debug("key: {} actual map value: managedFieldValue: {}", keyInActual, managedFieldValue);
    keepOnlyManagedFields(
        emptyMapValue,
        (Map<String, Object>) actualMapValue,
        (Map<String, Object>) managedFields.get(key),
        objectMapper);
  }

  @SuppressWarnings("unchecked")
  private static void removeIrrelevantValues(Map<String, Object> desiredMap) {
    var metadata = (Map<String, Object>) desiredMap.get(METADATA_KEY);
    metadata.remove(NAME_KEY);
    metadata.remove(NAMESPACE_KEY);
    IGNORED_METADATA.forEach(metadata::remove);
    if (metadata.isEmpty()) {
      desiredMap.remove(METADATA_KEY);
    }
    desiredMap.remove(KIND_KEY);
    desiredMap.remove(API_VERSION_KEY);
  }

  private static String getDiff(
      Map<String, Object> prunedActualMap,
      Map<String, Object> desiredMap,
      KubernetesSerialization serialization) {
    var actualYaml = serialization.asYaml(sortMap(prunedActualMap));
    var desiredYaml = serialization.asYaml(sortMap(desiredMap));
    if (log.isTraceEnabled()) {
      log.trace("Pruned actual resource:\n {} \ndesired resource:\n {} ", actualYaml, desiredYaml);
    }

    var patch = DiffUtils.diff(actualYaml.lines().toList(), desiredYaml.lines().toList());
    var unifiedDiff =
        UnifiedDiffUtils.generateUnifiedDiff("", "", actualYaml.lines().toList(), patch, 1);
    return String.join("\n", unifiedDiff);
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> sortMap(Map<String, Object> map) {
    var sortedKeys = new ArrayList<>(map.keySet());
    Collections.sort(sortedKeys);

    var sortedMap = new LinkedHashMap<String, Object>();
    for (var key : sortedKeys) {
      var value = map.get(key);
      if (value instanceof Map) {
        sortedMap.put(key, sortMap((Map<String, Object>) value));
      } else if (value instanceof List) {
        sortedMap.put(key, sortListItems((List<Object>) value));
      } else {
        sortedMap.put(key, value);
      }
    }
    return sortedMap;
  }

  @SuppressWarnings("unchecked")
  static List<Object> sortListItems(List<Object> list) {
    var sortedList = new ArrayList<>();
    for (var item : list) {
      if (item instanceof Map) {
        sortedList.add(sortMap((Map<String, Object>) item));
      } else if (item instanceof List) {
        sortedList.add(sortListItems((List<Object>) item));
      } else {
        sortedList.add(item);
      }
    }
    return sortedList;
  }

  private static String keyWithoutPrefix(String key) {
    return key.substring(2);
  }
}
