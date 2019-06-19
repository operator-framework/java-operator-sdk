package com.github.containersolutions.operator.spingboot.starter;

import com.github.containersolutions.operator.Context;
import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import com.github.containersolutions.operator.spingboot.starter.model.TestResource;
import com.github.containersolutions.operator.spingboot.starter.model.TestResourceDoneable;
import com.github.containersolutions.operator.spingboot.starter.model.TestResourceList;
import io.fabric8.kubernetes.client.CustomResource;

import java.util.Optional;

@Controller(group = "testGroup",
        kind = "testKind",
        customResourceClass = TestResource.class,
        customResourceListClass = TestResourceList.class,
        customResourceDonebaleClass = TestResourceDoneable.class)
public class TestController implements ResourceController {
    @Override
    public void deleteResource(CustomResource resource, Context context) {

    }

    @Override
    public Optional createOrUpdateResource(CustomResource resource, Context context) {
        return Optional.empty();
    }
}
