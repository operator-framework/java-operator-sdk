package com.github.containersolutions.operator.spingboot.starter;

import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import com.github.containersolutions.operator.spingboot.starter.model.TestResource;
import com.github.containersolutions.operator.spingboot.starter.model.TestResourceDoneable;
import com.github.containersolutions.operator.spingboot.starter.model.TestResourceList;
import io.fabric8.kubernetes.client.CustomResource;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Controller(
        crdName = "name",
        customResourceClass = TestResource.class,
        customResourceListClass = TestResourceList.class,
        customResourceDoneableClass = TestResourceDoneable.class)
public class TestController implements ResourceController {

    @Override
    public boolean deleteResource(CustomResource resource) {
        return true;
    }

    @Override
    public Optional createOrUpdateResource(CustomResource resource) {
        return Optional.empty();
    }
}
