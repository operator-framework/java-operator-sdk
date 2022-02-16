package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.zjsonpatch.JsonDiff;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DesiredValueMatcher implements ResourceMatcher {

  private final ObjectMapper objectMapper;

  public DesiredValueMatcher(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean match(HasMetadata actualResource, HasMetadata desiredResource, Context context) {
    if (actualResource instanceof Secret || actualResource instanceof ConfigMap) {
      // this comparison should be done ideally without metadata
      return actualResource.equals(desiredResource);
    }
    // reflection will be replaced by this:
    // https://github.com/fabric8io/kubernetes-client/issues/3816
    var desiredSpecNode = objectMapper.valueToTree(ReconcilerUtils.getSpec(desiredResource));
    var actualSpecNode = objectMapper.valueToTree(ReconcilerUtils.getSpec(actualResource));
    var diffJsonPatch = JsonDiff.asJson(desiredSpecNode, actualSpecNode);
    for (int i = 0; i < diffJsonPatch.size(); i++) {
      String operation = diffJsonPatch.get(i).get("op").asText();
      if (!operation.equals("add")) {
        return false;
      }
    }
    return true;
  }
}
