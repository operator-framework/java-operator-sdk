package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ManagedFieldsEntry;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.LoggingUtils;

/**
 * Matches the actual state on the server vs the desired state. Based on the managedFields of SSA.
 *
 * <p>
 * The basis of algorithm is to extract the fields managed we convert resources to Map/List
 * composition. The actual resource (from the server) is pruned, all the fields which are not
 * mentioed in managedFields of the target manager is removed. Some irrelevant fields are also
 * removed from desired. And the two resulted Maps are compared for equality. The implementation is
 * a bit nasty since have to deal with some specific cases of managedFields format.
 * </p>
 *
 * @param <R> matched resource type
 */
// https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#fieldsv1-v1-meta
// https://github.com/kubernetes-sigs/structured-merge-diff
// https://docs.aws.amazon.com/eks/latest/userguide/kubernetes-field-management.html
// see also: https://kubernetes.slack.com/archives/C0123CNN8F3/p1686141087220719
public class SSABasedGenericKubernetesResourceMatcher<R extends HasMetadata> {

  @SuppressWarnings("rawtypes")
  private static final SSABasedGenericKubernetesResourceMatcher INSTANCE =
      new SSABasedGenericKubernetesResourceMatcher<>();
  public static final String APPLY_OPERATION = "Apply";
  public static final String DOT_KEY = ".";

  private static final List<String> IGNORED_METADATA = Arrays.asList("creationTimestamp", "deletionTimestamp", "generation",
      "selfLink", "uid");

  @SuppressWarnings("unchecked")
  public static <L extends HasMetadata> SSABasedGenericKubernetesResourceMatcher<L> getInstance() {
    return INSTANCE;
  }

  private static final String F_PREFIX = "f:";
  private static final String K_PREFIX = "k:";
  private static final String V_PREFIX = "v:";
  private static final String METADATA_KEY = "metadata";
  private static final String NAME_KEY = "name";
  private static final String NAMESPACE_KEY = "namespace";
  private static final String KIND_KEY = "kind";
  private static final String API_VERSION_KEY = "apiVersion";

  private static final Logger log =
      LoggerFactory.getLogger(SSABasedGenericKubernetesResourceMatcher.class);


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

    sanitizeState(actual, desired, actualMap);

    var desiredMap = objectMapper.convertValue(desired, Map.class);
    if (LoggingUtils.isNotSensitiveResource(desired)) {
      log.trace("Original actual: \n {} \n original desired: \n {} ", actual, desiredMap);
    }

    var prunedActual = new HashMap<String, Object>(actualMap.size());
    keepOnlyManagedFields(prunedActual, actualMap,
        managedFieldsEntry.getFieldsV1().getAdditionalProperties(), objectMapper);

    removeIrrelevantValues(desiredMap);

    if (LoggingUtils.isNotSensitiveResource(desired)) {
      log.debug("Pruned actual: \n {} \n desired: \n {} ", prunedActual, desiredMap);
    }

    return prunedActual.equals(desiredMap);
  }

  /**
   * Correct for known issue with SSA
   */
  @SuppressWarnings("unchecked")
  private void sanitizeState(R actual, R desired, Map<String, Object> actualMap) {
    if (desired instanceof StatefulSet) {
      StatefulSet desiredStatefulSet = (StatefulSet) desired;
      StatefulSet actualStatefulSet = (StatefulSet) actual;
      int claims = desiredStatefulSet.getSpec().getVolumeClaimTemplates().size();
      if (claims == actualStatefulSet.getSpec().getVolumeClaimTemplates().size()) {
        for (int i = 0; i < claims; i++) {
          if (desiredStatefulSet.getSpec().getVolumeClaimTemplates().get(i).getSpec()
              .getVolumeMode() == null) {
            Optional
                .ofNullable(GenericKubernetesResource.get(actualMap, "spec", "volumeClaimTemplates",
                    i, "spec"))
                .map(Map.class::cast).ifPresent(m -> m.remove("volumeMode"));
          }
          if (desiredStatefulSet.getSpec().getVolumeClaimTemplates().get(i).getStatus() == null) {
            Optional
                .ofNullable(
                    GenericKubernetesResource.get(actualMap, "spec", "volumeClaimTemplates", i))
                .map(Map.class::cast).ifPresent(m -> m.remove("status"));
          }
        }
      }
    }
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

  @SuppressWarnings("unchecked")
  private static void keepOnlyManagedFields(Map<String, Object> result,
      Map<String, Object> actualMap,
      Map<String, Object> managedFields, KubernetesSerialization objectMapper) {

    if (managedFields.isEmpty()) {
      result.putAll(actualMap);
      return;
    }
    for (Map.Entry<String, Object> entry : managedFields.entrySet()) {
      String key = entry.getKey();
      if (key.startsWith(F_PREFIX)) {
        String keyInActual = keyWithoutPrefix(key);
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
            fillResultsAndTraverseFurther(result, actualMap, managedFields, objectMapper, key,
                keyInActual, managedFieldValue);
          }
        } else {
          // this should handle the case when the value is complex in the actual map (not just a
          // simple value).
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

  @SuppressWarnings("unchecked")
  private static void fillResultsAndTraverseFurther(Map<String, Object> result,
      Map<String, Object> actualMap, Map<String, Object> managedFields,
      KubernetesSerialization objectMapper, String key, String keyInActual,
      Object managedFieldValue) {
    var emptyMapValue = new HashMap<String, Object>();
    result.put(keyInActual, emptyMapValue);
    var actualMapValue = actualMap.getOrDefault(keyInActual, Collections.emptyMap());
    log.debug("key: {} actual map value: managedFieldValue: {}", keyInActual, managedFieldValue);

    keepOnlyManagedFields(emptyMapValue, (Map<String, Object>) actualMapValue,
        (Map<String, Object>) managedFields.get(key), objectMapper);
  }

  private static boolean isNestedValue(Map<?, ?> managedFieldValue) {
    return !managedFieldValue.isEmpty();
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
  private static void handleListKeyEntrySet(Map<String, Object> result,
      Map<String, Object> actualMap,
      KubernetesSerialization objectMapper, String keyInActual,
      Set<Entry<String, Object>> managedEntrySet) {
    var valueList = new ArrayList<>();
    result.put(keyInActual, valueList);
    var actualValueList = (List<Map<String, Object>>) actualMap.get(keyInActual);

    SortedMap<Integer, Map<String, Object>> targetValuesByIndex = new TreeMap<>();
    Map<Integer, Map<String, Object>> managedEntryByIndex = new HashMap<>();

    for (Map.Entry<String, Object> listEntry : managedEntrySet) {
      if (DOT_KEY.equals(listEntry.getKey())) {
        continue;
      }
      var actualListEntry = selectListEntryBasedOnKey(keyWithoutPrefix(listEntry.getKey()),
          actualValueList, objectMapper);
      targetValuesByIndex.put(actualListEntry.getKey(), actualListEntry.getValue());
      managedEntryByIndex.put(actualListEntry.getKey(), (Map<String, Object>) listEntry.getValue());
    }

    targetValuesByIndex.forEach((key, value) -> {
      var emptyResMapValue = new HashMap<String, Object>();
      valueList.add(emptyResMapValue);
      keepOnlyManagedFields(emptyResMapValue, value, managedEntryByIndex.get(key), objectMapper);
    });
  }

  /**
   * Set values, the "v:" prefix. Form in managed fields: "f:some-set":{"v:1":{}},"v:2":{},"v:3":{}}
   * Note that this should be just used in very rare cases, actually was not able to produce a
   * sample. Kubernetes developers who worked on this feature were not able to provide one either
   * when prompted. Basically this method just adds the values from {@code "v:<value>"} to the
   * result.
   */
  @SuppressWarnings("rawtypes")
  private static void handleSetValues(Map<String, Object> result, Map<String, Object> actualMap,
      KubernetesSerialization objectMapper, String keyInActual,
      Set<Entry<String, Object>> managedEntrySet) {
    var valueList = new ArrayList<>();
    result.put(keyInActual, valueList);
    for (Map.Entry<String, Object> valueEntry : managedEntrySet) {
      // not clear if this can happen
      if (DOT_KEY.equals(valueEntry.getKey())) {
        continue;
      }
      Class<?> targetClass = null;
      List values = (List) actualMap.get(keyInActual);
      if (!(values.get(0) instanceof Map)) {
        targetClass = values.get(0).getClass();
      }

      var value = parseKeyValue(keyWithoutPrefix(valueEntry.getKey()), targetClass, objectMapper);
      valueList.add(value);
    }
  }

  public static Object parseKeyValue(String stringValue, Class<?> targetClass,
      KubernetesSerialization objectMapper) {
    stringValue = stringValue.trim();
    if (targetClass != null) {
      return objectMapper.unmarshal(stringValue, targetClass);
    } else {
      return objectMapper.unmarshal(stringValue, Map.class);
    }
  }

  private static boolean isSetValueField(Set<Map.Entry<String, Object>> managedEntrySet) {
    return isKeyPrefixedSkippingDotKey(managedEntrySet, V_PREFIX);
  }

  private static boolean isListKeyEntrySet(Set<Map.Entry<String, Object>> managedEntrySet) {
    return isKeyPrefixedSkippingDotKey(managedEntrySet, K_PREFIX);
  }

  /**
   * Sometimes (not always) the first subfield of a managed field ("f:") is ".:{}", it looks that
   * those are added when there are more subfields of a referenced field. See test samples. Does not
   * seem to provide additional functionality, so can be just skipped for now.
   */
  private static boolean isKeyPrefixedSkippingDotKey(Set<Map.Entry<String, Object>> managedEntrySet,
      String prefix) {
    var iterator = managedEntrySet.iterator();
    var managedFieldEntry = iterator.next();
    if (managedFieldEntry.getKey().equals(DOT_KEY)) {
      managedFieldEntry = iterator.next();
    }
    return managedFieldEntry.getKey().startsWith(prefix);
  }

  @SuppressWarnings("unchecked")
  private static java.util.Map.Entry<Integer, Map<String, Object>> selectListEntryBasedOnKey(
      String key,
      List<Map<String, Object>> values,
      KubernetesSerialization objectMapper) {
    Map<String, Object> ids = objectMapper.unmarshal(key, Map.class);
    List<Map<String, Object>> possibleTargets = new ArrayList<>(1);
    int index = -1;
    for (int i = 0; i < values.size(); i++) {
      var v = values.get(i);
      if (v.entrySet().containsAll(ids.entrySet())) {
        possibleTargets.add(v);
        index = i;
      }
    }
    if (possibleTargets.isEmpty()) {
      throw new IllegalStateException(
          "Cannot find list element for key:" + key + ", in map: "
              + values.stream().map(Map::keySet).collect(Collectors.toList()));
    }
    if (possibleTargets.size() > 1) {
      throw new IllegalStateException(
          "More targets found in list element for key:" + key + ", in map: "
              + values.stream().map(Map::keySet).collect(Collectors.toList()));
    }
    final var finalIndex = index;
    return new AbstractMap.SimpleEntry<>(finalIndex, possibleTargets.get(0));
  }


  private Optional<ManagedFieldsEntry> checkIfFieldManagerExists(R actual, String fieldManager) {
    var targetManagedFields = actual.getMetadata().getManagedFields().stream()
        // Only the apply operations are interesting for us since those were created properly be SSA
        // Patch. An update can be present with same fieldManager when migrating and having the same
        // field manager name.
        .filter(
            f -> f.getManager().equals(fieldManager) && f.getOperation().equals(APPLY_OPERATION))
        .collect(Collectors.toList());
    if (targetManagedFields.isEmpty()) {
      log.debug("No field manager exists for resource {} with name: {} and operation Apply ",
          actual.getKind(), actual.getMetadata().getName());
      return Optional.empty();
    }
    // this should not happen in theory
    if (targetManagedFields.size() > 1) {
      throw new OperatorException(
          "More than one field manager exists with name: " + fieldManager + "in resource: " +
              actual.getKind() + " with name: " + actual.getMetadata().getName());
    }
    return Optional.of(targetManagedFields.get(0));
  }

  private static String keyWithoutPrefix(String key) {
    return key.substring(2);
  }

}
