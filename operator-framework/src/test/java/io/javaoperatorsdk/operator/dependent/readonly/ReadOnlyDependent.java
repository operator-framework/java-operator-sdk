package io.javaoperatorsdk.operator.dependent.readonly;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;

@KubernetesDependent
public class ReadOnlyDependent extends KubernetesDependentResource<ConfigMap, ConfigMapReader> {}
