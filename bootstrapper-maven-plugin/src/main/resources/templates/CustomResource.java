package {{groupId}};

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("{{groupId}}")
@Version("v1")
public class {{artifactClassId}}CustomResource extends CustomResource<{{artifactClassId}}Spec,{{artifactClassId}}Status> implements Namespaced {
}
