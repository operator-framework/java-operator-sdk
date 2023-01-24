package io.javaoperatorsdk.operator.processing.event.source.informer;

public interface EventHandler<T> {

    void onAdd(EventContext eventContext, T obj);

    void onUpdate(EventContext eventContext,T oldObj, T newObj);

    void onDelete(EventContext eventContext, T obj, boolean deletedFinalStateUnknown);
}
