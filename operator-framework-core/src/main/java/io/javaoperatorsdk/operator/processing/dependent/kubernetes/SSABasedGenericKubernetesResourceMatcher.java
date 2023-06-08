package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ManagedFieldsEntry;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Matches the actual state on the server vs the desired state. Based on the managedFields of SSA.
 *
 * The ide of algorithm is basically trivial, we convert resources to Map/List composition.
 * The actual resource (from the server) is pruned, all the fields which are not mentioed in managedFields
 * of the target manager is removed. Some irrelevant fields are also removed from desired. And the
 * two resulted Maps are compared for equality. The implementation is a bit nasty since have to deal with
 * some specific cases of managedFields format. 
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

  @SuppressWarnings("unchecked")
  public static <L extends HasMetadata> SSABasedGenericKubernetesResourceMatcher<L> getInstance() {
    return INSTANCE;
  }

  private static final TypeReference<HashMap<String, Object>> typeRef = new TypeReference<>() {};

  private static final String F_PREFIX = "f:";
  private static final String K_PREFIX = "k:";
  private static final String V_PREFIX = "v:";
  public static final String METADATA_KEY = "metadata";

  private static final Logger log =
      LoggerFactory.getLogger(SSABasedGenericKubernetesResourceMatcher.class);


  public boolean matches(R actual, R desired, Context<?> context) {
    try {
      var optionalManagedFieldsEntry =
          checkIfFieldManagerExists(actual,
              context.getControllerConfiguration().fieldManager());
      // the results of this is that it will add the field manager; it's important from migration
      // aspect
      if (optionalManagedFieldsEntry.isEmpty()) {
        return false;
      }

      var managedFieldsEntry = optionalManagedFieldsEntry.orElseThrow();

      var objectMapper =
          context.getControllerConfiguration().getConfigurationService().getObjectMapper();

      var actualMap = objectMapper.convertValue(actual, typeRef);
      var desiredMap = objectMapper.convertValue(desired, typeRef);

      log.trace("Original actual: \n {} \n original desired: \n {} ", actual, desiredMap);

      var prunedActual = new HashMap<String, Object>();
      pruneActualAccordingManagedFields(prunedActual, actualMap,
          managedFieldsEntry.getFieldsV1().getAdditionalProperties(), objectMapper);

      removeIrrelevantValues(desiredMap);

      log.debug("Pruned actual: \n {} \n desired: \n {} ", prunedActual, desiredMap);

      return prunedActual.equals(desiredMap);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private void removeIrrelevantValues(HashMap<String, Object> desiredMap) {
    var metadata = (Map<String, Object>) desiredMap.get(METADATA_KEY);
    metadata.remove("name");
    metadata.remove("namespace");
    if (metadata.isEmpty()) {
      desiredMap.remove(METADATA_KEY);
    }
    desiredMap.remove("kind");
    desiredMap.remove("apiVersion");
  }

  private void pruneActualAccordingManagedFields(Map<String, Object> result,
      Map<String, Object> actualMap,
      Map<String, Object> managedFields, ObjectMapper objectMapper) throws JsonProcessingException {

    if (managedFields.isEmpty()) {
      result.putAll(actualMap);
      return;
    }
    for (Map.Entry<String, Object> entry : managedFields.entrySet()) {
      String key = entry.getKey();
      if (key.startsWith(F_PREFIX)) {
        String keyInActual = keyWithoutPrefix(key);
        var managedFieldValue = entry.getValue();
        if (isNestedValue(managedFieldValue)) {
          var managedEntrySet = ((Map<String, Object>) managedFieldValue).entrySet();

          // two special cases "k:" and "v:" prefixes
          if (isListKeyEntrySet(managedEntrySet)) {
            handleListKeyEntrySet(result, actualMap, objectMapper, keyInActual, managedEntrySet);
          } else if (isSetValueField(managedEntrySet)) {
            handleSetValues(result, actualMap, objectMapper, keyInActual, managedEntrySet);
          } else {
            // basically if we should travers further
            fillResultsAndTraversFurther(result, actualMap, managedFields, objectMapper, key,
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

  private void fillResultsAndTraversFurther(Map<String, Object> result,
      Map<String, Object> actualMap, Map<String, Object> managedFields, ObjectMapper objectMapper,
      String key, String keyInActual, Object managedFieldValue) throws JsonProcessingException {
    var emptyMapValue = new HashMap<String, Object>();
    result.put(keyInActual, emptyMapValue);
    var actualMapValue = actualMap.get(keyInActual);
    log.debug("key: {} actual map value: {} managedFieldValue: {}", keyInActual,
        actualMapValue, managedFieldValue);

    pruneActualAccordingManagedFields(emptyMapValue, (Map<String, Object>) actualMapValue,
        (Map<String, Object>) managedFields.get(key), objectMapper);
  }

  private static boolean isNestedValue(Object managedFieldValue) {
    return managedFieldValue instanceof Map && (!((Map) managedFieldValue).isEmpty());
  }

  // list entries referenced by key, or when "k:" prefix is used
  private void handleListKeyEntrySet(Map<String, Object> result, Map<String, Object> actualMap,
      ObjectMapper objectMapper, String keyInActual, Set<Map.Entry<String, Object>> managedEntrySet) {
    var valueList = new ArrayList<>();
    result.put(keyInActual, valueList);
    var actualValueList = (List<Map<String, Object>>) actualMap.get(keyInActual);

    Map<Integer, Map<String, Object>> targetValuesByIndex = new HashMap<>();
    Map<Integer, Map<String, Object>> mangedEntryByIndex = new HashMap<>();

    for (Map.Entry<String, Object> listEntry : managedEntrySet) {
      if (DOT_KEY.equals(listEntry.getKey())) {
        continue;
      }
      var actualListEntry = selectListEntryBasedOnKey(keyWithoutPrefix(listEntry.getKey()),
          actualValueList, objectMapper);
      targetValuesByIndex.put(actualListEntry.getKey(), actualListEntry.getValue());
      mangedEntryByIndex.put(actualListEntry.getKey(), (Map<String, Object>) listEntry.getValue());
    }

    targetValuesByIndex.entrySet()
        .stream()
        // list is sorted according to the value in actual
        .sorted(Map.Entry.comparingByKey())
        .forEach(e -> {
          var emptyResMapValue = new HashMap<String, Object>();
          valueList.add(emptyResMapValue);
          try {
            pruneActualAccordingManagedFields(emptyResMapValue, e.getValue(),
                mangedEntryByIndex.get(e.getKey()), objectMapper);
          } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
          }
        });
  }

  // set values, the "v:" prefix
  private static void handleSetValues(Map<String, Object> result, Map<String, Object> actualMap,
      ObjectMapper objectMapper, String keyInActual,
      Set<Map.Entry<String, Object>> managedEntrySet) {
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

      var value =
          parseKeyValue(keyWithoutPrefix(valueEntry.getKey()), targetClass, objectMapper);
      valueList.add(value);
    }
  }

  public static Object parseKeyValue(String stringValue, Class<?> targetClass,
      ObjectMapper objectMapper) {
    try {
      stringValue = stringValue.trim();
      if (targetClass != null) {
        return objectMapper.readValue(stringValue, targetClass);
      } else {
        return objectMapper.readValue(stringValue, typeRef);
      }
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private boolean isSetValueField(Set<Map.Entry<String, Object>> managedEntrySet) {
    var iterator = managedEntrySet.iterator();
    var managedFieldEntry = iterator.next();
    if (managedFieldEntry.getKey().equals(DOT_KEY)) {
      managedFieldEntry = iterator.next();
    }
    return managedFieldEntry.getKey().startsWith(V_PREFIX);
  }

  private boolean isListKeyEntrySet(Set<Map.Entry<String, Object>> managedEntrySet) {
    var iterator = managedEntrySet.iterator();
    var managedFieldEntry = iterator.next();
    if (managedFieldEntry.getKey().equals(DOT_KEY)) {
      managedFieldEntry = iterator.next();
    }
    return managedFieldEntry.getKey().startsWith(K_PREFIX);
  }

  private java.util.Map.Entry<Integer, Map<String, Object>> selectListEntryBasedOnKey(String key,
      List<Map<String, Object>> values,
      ObjectMapper objectMapper) {
    try {
      Map<String, Object> ids = objectMapper.readValue(key, typeRef);
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
            "Cannot find list element for key:" + key + ", in map: " + values);
      }
      if (possibleTargets.size() > 1) {
        throw new IllegalStateException(
            "More targets found in list element for key:" + key + ", in map: " + values);
      }
      final var finalIndex = index;
      return new Map.Entry<>() {
        @Override
        public Integer getKey() {
          return finalIndex;
        }

        @Override
        public Map<String, Object> getValue() {
          return possibleTargets.get(0);
        }

        @Override
        public Map<String, Object> setValue(Map<String, Object> stringObjectMap) {
          throw new IllegalStateException("should not be called");
        }
      };
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
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
          actual, actual.getMetadata().getName());
      return Optional.empty();
    }
    // this should not happen in theory
    if (targetManagedFields.size() > 1) {
      log.debug("More field managers exists with name: {} in resource: {} with name: {} ",
          fieldManager,
          actual, actual.getMetadata().getName());
    }
    return Optional.of(targetManagedFields.get(0));
  }

  private static String keyWithoutPrefix(String key) {
    return key.substring(2);
  }

}
