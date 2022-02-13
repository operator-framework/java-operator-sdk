package io.javaoperatorsdk.operator.api.reconciler.dependent.matcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.zjsonpatch.JsonDiff;
import io.fabric8.zjsonpatch.JsonPatch;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;

public class SpecifiedValuesMatcher<R extends HasMetadata, P extends HasMetadata>
    implements ResourceMatcher<R, P> {

  // todo make this configurable
  private static final ObjectMapper mapper = new ObjectMapper();

  @Override
  public void onEventSourceInit(EventSourceContext<P> context) {
    ResourceMatcher.super.onEventSourceInit(context);
  }

  @Override
  public void onCreated(R desired, R created) {}

  @Override
  public boolean match(R actual, R desired, Context context) {
    if (desired instanceof ConfigMap || desired instanceof Secret) {
      throw new IllegalStateException("Not supported yet:" + desired.getClass());
    }
    var desiredSpecNode = mapper.valueToTree(ReconcilerUtils.getSpec(desired));
    var actualSpecNode = mapper.valueToTree(ReconcilerUtils.getSpec(actual));
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
