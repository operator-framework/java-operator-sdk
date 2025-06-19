package io.javaoperatorsdk.operator.api.config.informer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.processing.event.source.cache.BoundedItemStore;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_FOLLOW_CONTROLLER_NAMESPACE_CHANGES;
import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_LONG_VALUE_SET;
import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_VALUE_SET;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Informer {

  String name() default NO_VALUE_SET;

  /**
   * Specified which namespaces the associated informer monitors for custom resources events. If no
   * namespace is specified then which namespaces the informer will monitor will depend on the
   * context in which the informer is configured:
   *
   * <ul>
   *   <li>all namespaces if configuring a controller informer
   *   <li>the namespaces configured for the associated controller if configuring an event source
   * </ul>
   *
   * You can set a list of namespaces or use the following constants:
   *
   * <ul>
   *   <li>{@link Constants#WATCH_ALL_NAMESPACES}
   *   <li>{@link Constants#WATCH_CURRENT_NAMESPACE}
   *   <li>{@link Constants#SAME_AS_CONTROLLER}
   * </ul>
   *
   * @return the array of namespaces the associated informer monitors
   */
  String[] namespaces() default {Constants.SAME_AS_CONTROLLER};

  /**
   * Optional label selector used to identify the set of custom resources the associated informer
   * will act upon. The label selector can be made of multiple comma separated requirements that
   * acts as a logical AND operator.
   *
   * @return the label selector
   */
  String labelSelector() default NO_VALUE_SET;

  /**
   * Optional {@link OnAddFilter} to filter add events sent to the associated informer
   *
   * @return the {@link OnAddFilter} filter implementation to use, defaulting to the interface
   *     itself if no value is set
   */
  Class<? extends OnAddFilter> onAddFilter() default OnAddFilter.class;

  /**
   * Optional {@link OnUpdateFilter} to filter update events sent to the associated informer
   *
   * @return the {@link OnUpdateFilter} filter implementation to use, defaulting to the interface
   *     itself if no value is set
   */
  Class<? extends OnUpdateFilter> onUpdateFilter() default OnUpdateFilter.class;

  /**
   * Optional {@link OnDeleteFilter} to filter delete events sent to the associated informer
   *
   * @return the {@link OnDeleteFilter} filter implementation to use, defaulting to the interface
   *     itself if no value is set
   */
  Class<? extends OnDeleteFilter> onDeleteFilter() default OnDeleteFilter.class;

  /**
   * Optional {@link GenericFilter} to filter events sent to the associated informer
   *
   * @return the {@link GenericFilter} filter implementation to use, defaulting to the interface
   *     itself if no value is set
   */
  Class<? extends GenericFilter> genericFilter() default GenericFilter.class;

  /**
   * Set that in case of a runtime controller namespace changes, the informer should also follow the
   * new namespace set.
   */
  boolean followControllerNamespaceChanges() default DEFAULT_FOLLOW_CONTROLLER_NAMESPACE_CHANGES;

  /**
   * Replaces the item store used by the informer for the associated primary resource controller.
   * See underlying <a href=
   * "https://github.com/fabric8io/kubernetes-client/blob/43b67939fde91046ab7fb0c362f500c2b46eb59e/kubernetes-client/src/main/java/io/fabric8/kubernetes/client/informers/impl/DefaultSharedIndexInformer.java#L273">
   * method</a> in fabric8 client informer implementation.
   *
   * <p>The main goal, is to be able to use limited caches or provide any custom implementation.
   *
   * <p>See {@link BoundedItemStore} and <a href=
   * "https://github.com/operator-framework/java-operator-sdk/blob/main/caffeine-bounded-cache-support/src/main/java/io/javaoperatorsdk/operator/processing/event/source/cache/CaffeineBoundedCache.java">CaffeinBoundedCache</a>
   *
   * @return the class of the {@link ItemStore} implementation to use
   */
  Class<? extends ItemStore> itemStore() default ItemStore.class;

  /**
   * The maximum amount of items to return for a single list call when starting the primary resource
   * related informers. If this is a not null it will result in paginating for the initial load of
   * the informer cache.
   */
  long informerListLimit() default NO_LONG_VALUE_SET;

  /** Field selector for informer, for matching field with the value: field=value; */
  Field[] withFields() default {};

  /** Negated field selector for informer, for Non-matching field with the value: field!=value; */
  Field[] withoutFields() default {};
}
