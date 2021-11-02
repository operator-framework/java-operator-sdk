package io;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import java.io.Serializable;


public abstract class AbstractReconciler<T extends CustomResource<?,?>> implements Serializable,
        Reconciler<T> {

  public static class MyCustomResource extends CustomResource<Void,Void> {

  }
}
