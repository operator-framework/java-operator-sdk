package io.javaoperatorsdk.operator.api.reconciler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class FinalizerUtils {

  private static final Logger log = LoggerFactory.getLogger(FinalizerUtils.class);

  // todo SSA

  public static <P extends HasMetadata> P patchFinalizer(
      P resource, String finalizer, Context<P> context) {
    return PrimaryUpdateAndCacheUtils.updateAndCacheResource(
        resource,
        context,
        r -> r,
        r ->
            context
                .getClient()
                .resource(r)
                .edit(
                    res -> {
                      res.addFinalizer(finalizer);
                      return res;
                    }));
  }

  public static <P extends HasMetadata> P removeFinalizer(
      P resource, String finalizer, Context<P> context) {

    return PrimaryUpdateAndCacheUtils.updateAndCacheResource(
        resource,
        context,
        r -> r,
        r ->
            context
                .getClient()
                .resource(r)
                .edit(
                    res -> {
                      res.removeFinalizer(finalizer);
                      return res;
                    }));
  }
}
