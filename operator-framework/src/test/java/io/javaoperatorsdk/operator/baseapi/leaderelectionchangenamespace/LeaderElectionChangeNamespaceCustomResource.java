package io.javaoperatorsdk.operator.baseapi.leaderelectionchangenamespace;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("lcn")
public class LeaderElectionChangeNamespaceCustomResource extends CustomResource<Void, Void>
    implements Namespaced {}
