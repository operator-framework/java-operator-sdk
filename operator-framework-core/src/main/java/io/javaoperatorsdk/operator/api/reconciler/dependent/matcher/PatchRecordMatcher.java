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

import java.util.concurrent.locks.ReentrantLock;

// an alternative variant could be per resource patch - problem is there needs to be
// cleanup implemented
// problems:
// - with service the clusterIP can change later (not clear if this is just a legacy stuff on minikube todo test)

public class PatchRecordMatcher<R extends HasMetadata, P extends HasMetadata>
    implements ResourceMatcher<R, P> {

  // todo make this configurable
  private static final ObjectMapper mapper = new ObjectMapper();
  private volatile JsonNode diffJsonPatch = null;
  private final ReentrantLock lock = new ReentrantLock();

  @Override
  public void onEventSourceInit(EventSourceContext<P> context) {
    // todo object mapper from config?!
  }

  @Override
  public void onCreated(R desired, R created) {
    if (desired instanceof ConfigMap || desired instanceof Secret) {
      return;
    }
    if (diffJsonPatch == null) {
      // making sure diff is calculated only once
      lock.lock();
      try {
        if (diffJsonPatch != null) return;
        var desiredSpecNode = mapper.valueToTree(ReconcilerUtils.getSpec(desired));
        var createdSpecNode = mapper.valueToTree(ReconcilerUtils.getSpec(created));
        diffJsonPatch = JsonDiff.asJson(desiredSpecNode, createdSpecNode);
      } finally {
        lock.unlock();
      }
    }
  }

  @Override
  public boolean match(R actual, R desired, Context context) {
    if (desired instanceof ConfigMap || desired instanceof Secret) {
      throw new IllegalStateException("Not supported yet:"+desired.getClass());
    }
    var desiredSpecNode = mapper.valueToTree(ReconcilerUtils.getSpec(desired));
    var createdSpecNode = mapper.valueToTree(ReconcilerUtils.getSpec(actual));
    desiredSpecNode = JsonPatch.apply(diffJsonPatch,desiredSpecNode);
    return desiredSpecNode.equals(createdSpecNode);
  }
}
